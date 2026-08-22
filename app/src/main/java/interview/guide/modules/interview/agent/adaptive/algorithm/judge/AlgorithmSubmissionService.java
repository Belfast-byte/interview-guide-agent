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
    persistenceService.validateSubmission(new CreateSandboxExecution(
        submission.sessionId(),
        submission.turnIndex(),
        submission.problemId(),
        submission.language(),
        null,
        null,
        submission.runMode()
    ));
    StoredAlgorithmSource source = sourceStorage.store(
        submission.sessionId(),
        submission.language(),
        submission.source()
    );
    SandboxExecution execution = persistenceService.createPending(
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

  public SandboxExecution get(String executionId) {
    return persistenceService.getExecution(executionId);
  }

  public SandboxExecution get(String sessionId, String executionId) {
    SandboxExecution execution = get(executionId);
    if (!execution.sessionId().equals(sessionId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "判题提交不存在");
    }
    return execution;
  }

  public SandboxExecution getLatest(String sessionId, int turnIndex) {
    return persistenceService.getLatestExecution(sessionId, turnIndex);
  }
}
