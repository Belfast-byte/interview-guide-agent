package interview.guide.modules.interview.agent.adaptive.algorithm;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlgorithmPersistenceService {

  private final AlgorithmProblemRepository problemRepository;
  private final SandboxExecutionRepository executionRepository;
  private final SandboxExecutionLogRepository logRepository;
  private final AlgorithmSessionFacts sessionFacts;
  private final AlgorithmInterviewProperties properties;

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

  @Transactional
  public SandboxExecution createPending(CreateSandboxExecution command) {
    long turnId = sessionFacts.lockCurrentTurn(command.sessionId(), command.turnIndex());
    if (!problemRepository.existsById(command.problemId())) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "算法题不存在");
    }
    long submitted = executionRepository.countBySessionId(command.sessionId());
    if (submitted >= properties.getMaxExecutionsPerSession()) {
      throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "本场面试代码执行次数已达上限");
    }
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
    return executionRepository.save(entity).toDomain();
  }

  @Transactional(readOnly = true)
  public SandboxExecution getExecution(String executionId) {
    return executionRepository.findById(executionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "判题提交不存在"))
        .toDomain();
  }

  @Transactional(readOnly = true)
  public boolean executionExists(String executionId) {
    return executionRepository.existsById(executionId);
  }

  @Transactional
  public boolean markRunning(String executionId) {
    return executionRepository.findLockedById(executionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "判题提交不存在"))
        .markRunning();
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
    return retry;
  }

  @Transactional
  public boolean resetAfterWorkerFailure(String executionId) {
    return executionRepository.findLockedById(executionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "判题提交不存在"))
        .resetAfterWorkerFailure();
  }

  @Transactional
  public void markInfrastructureFailure(String executionId) {
    executionRepository.findLockedById(executionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "判题提交不存在"))
        .markInfrastructureFailure();
  }
}
