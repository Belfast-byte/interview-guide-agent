package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CodeAnalysisResultAcceptanceService {

  private final CodeAnalysisPersistenceService persistenceService;
  private final CodeAnchorCatalog anchorCatalog;

  public void accept(String jobId, CodeAnalysisResult result) {
    ProjectRepositorySnapshot snapshot = persistenceService.getRepositorySnapshot(jobId);
    if (!snapshot.commitHash().equals(result.digest().commitHash())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "分析产物 commitHash 不匹配");
    }
    Set<CodeAnchor> anchors = anchors(result);
    Set<CodeAnchor> missing = anchorCatalog.findMissing(snapshot.repositoryRef(), anchors);
    if (!missing.isEmpty()) {
      throw new BusinessException(
          ErrorCode.BAD_REQUEST,
          "代码分析产物包含不存在的锚点: " + missing.iterator().next().display()
      );
    }
    persistenceService.complete(jobId, result);
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
