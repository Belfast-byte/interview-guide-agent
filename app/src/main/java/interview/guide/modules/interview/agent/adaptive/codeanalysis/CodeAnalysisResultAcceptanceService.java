package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.claim.ClaimVerification;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisPersistenceService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisProperties;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.ProjectRepositorySnapshot;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import interview.guide.modules.interview.agent.adaptive.observability.CodeAnalysisTelemetry;

/**
 * 代码分析结果验收服务，校验结果锚点和来源可信度。
 */
@Service
@RequiredArgsConstructor
public class CodeAnalysisResultAcceptanceService {

  private final CodeAnalysisPersistenceService persistenceService;
  private final CodeAnchorCatalog anchorCatalog;
  private final CodeAnalysisProperties properties;
  private final CodeAnalysisTelemetry telemetry;

  public void accept(String jobId, CodeAnalysisResult result) {
    ProjectRepositorySnapshot snapshot = persistenceService.getRepositorySnapshot(jobId);
    if (!snapshot.commitHash().equals(result.digest().commitHash())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "分析产物 commitHash 不匹配");
    }
    if (result.tokenCost() > properties.getMaxTokenCost()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "代码分析 token 成本超过上限");
    }
    Set<CodeAnchor> anchors = anchors(result);
    Set<CodeAnchor> missing = anchorCatalog.findMissing(
        snapshot.tenantId(),
        snapshot.sessionId(),
        snapshot.repositoryRef(),
        anchors
    );
    if (!missing.isEmpty()) {
      telemetry.anchorRejected();
      throw new BusinessException(
          ErrorCode.BAD_REQUEST,
          "代码分析产物包含不存在的锚点: " + missing.iterator().next().display()
      );
    }
    persistenceService.complete(jobId, result);
    telemetry.anchorsAccepted(anchors.size());
    telemetry.jobCompleted(result.durationMs(), result.tokenCost());
  }

  private Set<CodeAnchor> anchors(CodeAnalysisResult result) {
    Set<CodeAnchor> anchors = new LinkedHashSet<>();
    result.digest().modules().forEach(module -> addRequired(anchors, module.anchor()));
    result.digest().highlightCandidates()
        .forEach(finding -> addRequired(anchors, finding.anchor()));
    result.digest().riskSpots().forEach(finding -> addRequired(anchors, finding.anchor()));
    result.claimVerifications().stream()
        .flatMap(claim -> claim.codeFacts().stream())
        .map(ClaimVerification.CodeFact::anchor)
        .filter(anchor -> anchor != null)
        .forEach(anchors::add);
    result.scenarios().forEach(scenario -> addRequired(anchors, scenario.anchor()));
    return anchors;
  }

  private void addRequired(Set<CodeAnchor> anchors, CodeAnchor anchor) {
    if (anchor == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "代码分析产物缺少锚点");
    }
    anchors.add(anchor);
  }
}
