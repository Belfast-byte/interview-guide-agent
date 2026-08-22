package interview.guide.modules.interview.agent.adaptive.algorithm.judge;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmInterviewProperties;
import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmSessionFacts;
import interview.guide.modules.interview.agent.adaptive.algorithm.problem.AlgorithmDifficulty;
import interview.guide.modules.interview.agent.adaptive.algorithm.problem.AlgorithmProblem;
import interview.guide.modules.interview.agent.adaptive.algorithm.problem.AlgorithmProblemEntity;
import interview.guide.modules.interview.agent.adaptive.algorithm.problem.AlgorithmProblemRepository;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.CreateSandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionEntity;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionLogEntity;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionLogRepository;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionRepository;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionResult;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionStatus;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxWorkloadType;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import jakarta.annotation.PostConstruct;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 算法面试持久化服务，管理题目、提交和执行记录。
 */
@Service
@RequiredArgsConstructor
public class AlgorithmPersistenceService {

  private final AlgorithmProblemRepository problemRepository;
  private final SandboxExecutionRepository executionRepository;
  private final SandboxExecutionLogRepository logRepository;
  private final AlgorithmSessionFacts sessionFacts;
  private final AlgorithmInterviewProperties properties;
  private final AlgorithmInterviewTelemetry telemetry;

  @PostConstruct
  void initializeQueueDepth() {
    updateQueueDepth();
  }

  @Transactional
  public AlgorithmProblem saveProblem(AlgorithmProblem problem) {
    return problemRepository.save(new AlgorithmProblemEntity(problem)).toDomain();
  }

  @Transactional(readOnly = true)
  public AlgorithmProblem getProblem(String problemId) {
    return problemRepository.findById(problemId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "算法题不存在"))
        .toDomain();
  }

  @Transactional(readOnly = true)
  public List<AlgorithmProblem> findVariants(
      String variantGroup,
      AlgorithmDifficulty difficulty
  ) {
    return problemRepository.findByVariantGroupAndDifficultyOrderById(
        variantGroup,
        difficulty
    ).stream().map(AlgorithmProblemEntity::toDomain).toList();
  }

  @Transactional(readOnly = true)
  public List<String> attemptedProblemIds(String sessionId) {
    return executionRepository.findDistinctProblemIdsBySessionIdAndWorkloadType(
        sessionId,
        SandboxWorkloadType.ALGORITHM
    );
  }

  /**
   * 提交前置校验：轮次归属、题目存在性与执行配额。在源码上传前调用，避免被拒提交留下孤儿文件。
   */
  @Transactional
  public void validateSubmission(CreateSandboxExecution command) {
    sessionFacts.lockCurrentTurn(command.sessionId(), command.turnIndex());
    validateProblemAndQuota(command);
  }

  @Transactional
  public SandboxExecution createPending(CreateSandboxExecution command) {
    long turnId = sessionFacts.lockCurrentTurn(command.sessionId(), command.turnIndex());
    validateProblemAndQuota(command);
    int submissionSeq = executionRepository
        .findTopBySessionIdOrderBySubmissionSeqDesc(command.sessionId())
        .map(entity -> entity.toDomain().submissionSeq() + 1)
        .orElse(1);
    SandboxExecutionEntity entity = new SandboxExecutionEntity(
        UUID.randomUUID().toString(),
        command,
        turnId,
        submissionSeq
    );
    executionRepository.findBySessionIdAndTurnIdAndSupersededByIsNull(
        command.sessionId(),
        turnId
    ).stream()
        .filter(previous -> previous.hasDifferentCode(command.codeHash()))
        .forEach(previous -> previous.supersedeWith(entity.id()));
    SandboxExecution execution = executionRepository.save(entity).toDomain();
    telemetry.submissionAccepted();
    updateQueueDepth();
    return execution;
  }

  @Transactional(readOnly = true)
  public SandboxExecution getExecution(String executionId) {
    return executionRepository.findById(executionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "判题提交不存在"))
        .toDomain();
  }

  @Transactional(readOnly = true)
  public SandboxExecution getLatestExecution(String sessionId, int turnIndex) {
    long turnId = sessionFacts.turnId(sessionId, turnIndex);
    return executionRepository
        .findTopBySessionIdAndTurnIdOrderBySubmissionSeqDesc(sessionId, turnId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "判题提交不存在"))
        .toDomain();
  }

  @Transactional(readOnly = true)
  public boolean executionExists(String executionId) {
    return executionRepository.existsById(executionId);
  }

  @Transactional
  public boolean markRunning(String executionId) {
    boolean marked = executionRepository.findLockedById(executionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "判题提交不存在"))
        .markRunning();
    updateQueueDepth();
    return marked;
  }

  @Transactional
  public boolean applyResult(String executionId, SandboxExecutionResult result) {
    SandboxExecutionEntity execution = executionRepository.findLockedById(executionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "判题提交不存在"));
    boolean retry = execution.apply(result);
    if (!retry) {
      logRepository.saveAll(result.logs().stream()
          .map(log -> new SandboxExecutionLogEntity(execution.id(), log))
          .toList());
    }
    updateQueueDepth();
    return retry;
  }

  @Transactional
  public boolean resetAfterWorkerFailure(String executionId) {
    boolean reset = executionRepository.findLockedById(executionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "判题提交不存在"))
        .resetAfterWorkerFailure();
    updateQueueDepth();
    return reset;
  }

  @Transactional
  public SandboxExecution markInfrastructureFailure(String executionId) {
    SandboxExecutionEntity execution = executionRepository.findLockedById(executionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "判题提交不存在"));
    execution.markInfrastructureFailure();
    updateQueueDepth();
    return execution.toDomain();
  }

  @Transactional
  public List<SandboxExecution> timeoutQueuedBefore(LocalDateTime cutoff) {
    List<SandboxExecution> timedOut = executionRepository.findByStatusAndCreatedAtBefore(
        SandboxExecutionStatus.PENDING,
        cutoff
    ).stream()
        .filter(SandboxExecutionEntity::markQueuedTimeout)
        .map(SandboxExecutionEntity::toDomain)
        .toList();
    updateQueueDepth();
    return timedOut;
  }

  @Transactional
  public List<SandboxExecution> timeoutRunningBefore(LocalDateTime cutoff) {
    List<SandboxExecution> timedOut = executionRepository.findByStatusAndStartedAtBefore(
        SandboxExecutionStatus.RUNNING,
        cutoff
    ).stream()
        .filter(SandboxExecutionEntity::markStuckRunningTimeout)
        .map(SandboxExecutionEntity::toDomain)
        .toList();
    updateQueueDepth();
    return timedOut;
  }

  private void validateProblemAndQuota(CreateSandboxExecution command) {
    if (command.workloadType() == SandboxWorkloadType.ALGORITHM
        && !problemRepository.existsById(command.problemId())) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "算法题不存在");
    }
    long submitted = executionRepository.countBySessionId(command.sessionId());
    if (submitted >= properties.getMaxExecutionsPerSession()) {
      telemetry.quotaRejected();
      throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "本场面试代码执行次数已达上限");
    }
    if (command.workloadType() == SandboxWorkloadType.PATCH
        && executionRepository.countBySessionIdAndWorkloadType(
            command.sessionId(),
            SandboxWorkloadType.PATCH
        ) >= properties.getMaxPatchExecutionsPerSession()) {
      telemetry.quotaRejected();
      throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "本场面试 PATCH 实操次数已达上限");
    }
  }

  private void updateQueueDepth() {
    telemetry.queueDepth(executionRepository.countByStatus(SandboxExecutionStatus.PENDING));
  }
}
