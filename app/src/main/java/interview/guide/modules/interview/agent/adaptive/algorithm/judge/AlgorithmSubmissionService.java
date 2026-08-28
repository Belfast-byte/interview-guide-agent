package interview.guide.modules.interview.agent.adaptive.algorithm.judge;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.algorithm.problem.AlgorithmSourceStorage;
import interview.guide.modules.interview.agent.adaptive.algorithm.problem.StoredAlgorithmSource;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.CreateSandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 算法提交服务，将代码提交到沙箱并记录执行。
 */
@Service
@RequiredArgsConstructor
public class AlgorithmSubmissionService {

  private final AlgorithmSourceStorage sourceStorage;
  private final AlgorithmPersistenceService persistenceService;
  private final AlgorithmJudgeStreamProducer producer;

  public SandboxExecution submit(SubmitAlgorithmCode submission) {
    return submit(submission, java.util.UUID.randomUUID().toString());
  }

  public SandboxExecution submit(
      SubmitAlgorithmCode submission,
      String idempotencyKey
  ) {
    if (persistenceService.executionExists(idempotencyKey)) {
      return persistenceService.getExecution(idempotencyKey);
    }
    StoredAlgorithmSource source = sourceStorage.store(
        submission.sessionId(),
        submission.language(),
        submission.source()
    );
    SandboxExecution execution = persistenceService.createPending(
        idempotencyKey,
        new CreateSandboxExecution(
            submission.sessionId(),
            submission.turnIndex(),
            submission.problemId(),
            submission.language(),
            source.codeRef(),
            source.codeHash(),
            submission.runMode()
        )
    );
    if (!producer.sendExecution(execution.id())) {
      persistenceService.markInfrastructureFailure(execution.id());
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "判题任务入队失败");
    }
    return execution;
  }

  public SandboxExecution get(String sessionId, String executionId) {
    return persistenceService.getExecution(sessionId, executionId);
  }

  public SandboxExecution getLatest(String sessionId, int turnIndex) {
    return persistenceService.getLatestExecution(sessionId, turnIndex);
  }
}
