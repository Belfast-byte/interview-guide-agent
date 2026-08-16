package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.claim.ClaimVerification;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.claim.ClaimVerificationRepository;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.AnalysisJobEntity;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.AnalysisJobRepository;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.AnalysisJobStatus;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisJob;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.ProjectDigest;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.ProjectDigestRepository;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario.ScenarioCard;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario.ScenarioCardRepository;
import interview.guide.modules.interview.agent.adaptive.core.context.ProjectInterviewContext;
import interview.guide.modules.interview.agent.adaptive.planning.ProjectPlanningContext;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 代码分析面试上下文服务，将项目代码分析结果装配进面试上下文。
 */
@Service
@RequiredArgsConstructor
public class CodeAnalysisInterviewContextService {

  private final AnalysisJobRepository jobRepository;
  private final ProjectDigestRepository digestRepository;
  private final ClaimVerificationRepository claimRepository;
  private final ScenarioCardRepository scenarioRepository;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public Optional<ProjectInterviewContext> findForSession(String sessionId) {
    return jobRepository.findTopBySessionIdAndStatusOrderByCreatedAtDesc(
        sessionId,
        AnalysisJobStatus.COMPLETED
    ).map(AnalysisJobEntity::toDomain)
        .map(this::map);
  }

  @Transactional(readOnly = true)
  public Optional<ProjectPlanningContext> findPlanningForSession(String sessionId) {
    return jobRepository.findTopBySessionIdAndStatusOrderByCreatedAtDesc(
        sessionId,
        AnalysisJobStatus.COMPLETED
    ).map(AnalysisJobEntity::toDomain)
        .map(job -> read(
            digestRepository.findByRepositoryId(job.repositoryId()).orElseThrow().payloadJson(),
            ProjectDigest.class
        ))
        .map(digest -> new ProjectPlanningContext(
            digest.digestId(),
            digest.commitHash(),
            digest.stack(),
            digest.modules().stream()
                .map(module -> new ProjectPlanningContext.ProjectModule(
                    module.name(),
                    module.role(),
                    module.anchor().display()
                ))
                .toList(),
            digest.highlightCandidates().stream()
                .map(finding -> new ProjectPlanningContext.ProjectFinding(
                    finding.title(),
                    finding.anchor().display(),
                    finding.why()
                ))
                .toList(),
            digest.riskSpots().stream()
                .map(finding -> new ProjectPlanningContext.ProjectFinding(
                    finding.title(),
                    finding.anchor().display(),
                    finding.why()
                ))
                .toList()
        ));
  }

  private ProjectInterviewContext map(CodeAnalysisJob job) {
    ProjectDigest digest = read(
        digestRepository.findByRepositoryId(job.repositoryId()).orElseThrow().payloadJson(),
        ProjectDigest.class
    );
    return new ProjectInterviewContext(
        digest.digestId(),
        claimRepository.findByRepositoryIdOrderByClaimId(job.repositoryId()).stream()
            .map(entity -> read(entity.payloadJson(), ClaimVerification.class))
            .map(claim -> new ProjectInterviewContext.ProjectClaim(
                claim.claimId(),
                claim.claim(),
                claim.status().name(),
                claim.codeFacts().stream()
                    .map(fact -> new ProjectInterviewContext.ProjectCodeFact(
                        fact.finding(),
                        fact.anchor() == null ? null : fact.anchor().display()
                    ))
                    .toList()
            ))
            .toList(),
        scenarioRepository.findByRepositoryIdOrderByScenarioId(job.repositoryId()).stream()
            .map(entity -> read(entity.payloadJson(), ScenarioCard.class))
            .map(scenario -> new ProjectInterviewContext.ProjectScenario(
                scenario.scenarioId(),
                scenario.title(),
                scenario.context(),
                scenario.anchor().display(),
                scenario.taskType().name(),
                scenario.constraints(),
                scenario.testsRef()
            ))
            .toList()
    );
  }

  private <T> T read(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JacksonException e) {
      throw new BusinessException(
          ErrorCode.INTERNAL_ERROR,
          "已存储的代码分析产物无效",
          e
      );
    }
  }
}
