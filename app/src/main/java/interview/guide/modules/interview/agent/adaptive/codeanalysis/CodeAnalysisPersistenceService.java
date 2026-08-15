package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.persistence.AdaptiveAgentSessionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class CodeAnalysisPersistenceService {

  private final AdaptiveAgentSessionRepository sessionRepository;
  private final ProjectRepoRepository repoRepository;
  private final AnalysisJobRepository jobRepository;
  private final ProjectDigestRepository digestRepository;
  private final ClaimVerificationRepository claimRepository;
  private final ScenarioCardRepository scenarioRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public CodeAnalysisJob createJob(
      String sessionId,
      String tenantId,
      String repositoryRef,
      String commitHash,
      LocalDateTime expiresAt
  ) {
    if (!sessionRepository.existsById(sessionId)) {
      throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND, "Agent 面试会话不存在");
    }
    ProjectRepoEntity repository = repoRepository
        .findBySessionIdAndCommitHash(sessionId, commitHash)
        .orElseGet(() -> repoRepository.save(new ProjectRepoEntity(
            UUID.randomUUID().toString(),
            sessionId,
            tenantId,
            repositoryRef,
            commitHash,
            expiresAt
        )));
    return jobRepository.findTopByRepositoryIdOrderByCreatedAtDesc(repository.id())
        .map(AnalysisJobEntity::toDomain)
        .orElseGet(() -> jobRepository.save(new AnalysisJobEntity(
            UUID.randomUUID().toString(),
            sessionId,
            repository.id()
        )).toDomain());
  }

  @Transactional
  public void complete(String jobId, CodeAnalysisResult result) {
    AnalysisJobEntity job = findJob(jobId);
    ProjectRepoEntity repository = repoRepository.findById(job.toDomain().repositoryId())
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "代码仓库快照不存在"));
    if (!repository.commitHash().equals(result.digest().commitHash())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "分析产物 commitHash 不匹配");
    }
    digestRepository.save(new ProjectDigestEntity(
        result.digest().digestId(),
        repository.id(),
        result.digest().commitHash(),
        serialize(result.digest())
    ));
    claimRepository.saveAll(result.claimVerifications().stream()
        .map(claim -> new ClaimVerificationEntity(
            claim.claimId(),
            repository.id(),
            serialize(claim)
        ))
        .toList());
    scenarioRepository.saveAll(result.scenarios().stream()
        .map(scenario -> new ScenarioCardEntity(
            scenario.scenarioId(),
            repository.id(),
            serialize(scenario)
        ))
        .toList());
    job.complete(result.durationMs(), result.tokenCost());
  }

  @Transactional(readOnly = true)
  public CodeAnalysisJob getJob(String sessionId, String jobId) {
    return jobRepository.findByIdAndSessionId(jobId, sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "代码分析任务不存在"))
        .toDomain();
  }

  @Transactional(readOnly = true)
  public ProjectDigest getDigest(String sessionId, String jobId) {
    CodeAnalysisJob job = getJob(sessionId, jobId);
    return deserialize(
        digestRepository.findByRepositoryId(job.repositoryId())
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "项目摘要尚未生成"))
            .payloadJson(),
        ProjectDigest.class
    );
  }

  @Transactional(readOnly = true)
  public List<ClaimVerification> getClaimVerifications(String sessionId, String jobId) {
    CodeAnalysisJob job = getJob(sessionId, jobId);
    return claimRepository.findByRepositoryIdOrderByClaimId(job.repositoryId()).stream()
        .map(entity -> deserialize(entity.payloadJson(), ClaimVerification.class))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ScenarioCard> getScenarios(String sessionId, String jobId) {
    CodeAnalysisJob job = getJob(sessionId, jobId);
    return scenarioRepository.findByRepositoryIdOrderByScenarioId(job.repositoryId()).stream()
        .map(entity -> deserialize(entity.payloadJson(), ScenarioCard.class))
        .toList();
  }

  private AnalysisJobEntity findJob(String jobId) {
    return jobRepository.findById(jobId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "代码分析任务不存在"));
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException e) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "代码分析产物序列化失败", e);
    }
  }

  private <T> T deserialize(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JacksonException e) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "代码分析产物反序列化失败", e);
    }
  }
}
