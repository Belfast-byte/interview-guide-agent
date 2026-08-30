package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import interview.guide.modules.interview.agent.adaptive.codeanalysis.claim.ClaimVerification;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.claim.ClaimVerificationRepository;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisJob;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisPersistenceService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.ProjectDigest;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.ProjectDigestRepository;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario.ScenarioCard;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario.ScenarioCardRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 代码分析面试上下文服务，将项目代码分析结果装配进面试上下文。
 */
@Service
@RequiredArgsConstructor
public class CodeAnalysisInterviewContextService {

  private final CodeAnalysisPersistenceService persistenceService;
  private final ProjectDigestRepository digestRepository;
  private final ClaimVerificationRepository claimRepository;
  private final ScenarioCardRepository scenarioRepository;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public Optional<ProjectInterviewContext> findForSession(String sessionId) {
    return persistenceService.findLatestCompletedJob(sessionId)
        .map(this::map);
  }

  private ProjectInterviewContext map(CodeAnalysisJob job) {
    ProjectDigest digest = CodeAnalysisJson.read(
        objectMapper,
        digestRepository.findByRepositoryId(job.repositoryId()).orElseThrow().payloadJson(),
        ProjectDigest.class
    );
    return new ProjectInterviewContext(
        digest.digestId(),
        claimRepository.findByRepositoryIdOrderByClaimId(job.repositoryId()).stream()
            .map(entity -> CodeAnalysisJson.read(
                objectMapper,
                entity.payloadJson(),
                ClaimVerification.class
            ))
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
            .map(entity -> CodeAnalysisJson.read(
                objectMapper,
                entity.payloadJson(),
                ScenarioCard.class
            ))
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
}
