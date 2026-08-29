package interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.algorithm.judge.AlgorithmJudgeStreamProducer;
import interview.guide.modules.interview.agent.adaptive.algorithm.judge.AlgorithmPersistenceService;
import interview.guide.modules.interview.agent.adaptive.algorithm.problem.AlgorithmSourceStorage;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.CreateSandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionStatus;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxWorkloadType;
import interview.guide.modules.interview.agent.adaptive.algorithm.problem.StoredAlgorithmSource;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 代码补丁提交服务。
 */
@Service
@RequiredArgsConstructor
public class CodePatchSubmissionService {

  private final CodeAnalysisPersistenceService codeAnalysisPersistenceService;
  private final AlgorithmSourceStorage sourceStorage;
  private final AlgorithmPersistenceService sandboxPersistenceService;
  private final AlgorithmJudgeStreamProducer producer;

  public SandboxExecution submit(PatchCodeSubmission submission) {
    PatchScenarioTarget target = codeAnalysisPersistenceService.getPatchTarget(
        submission.sessionId(),
        submission.scenarioId()
    );
    if (target.testsRef() == null || target.testsRef().isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "PATCH 场景缺少预置测试");
    }
    CreateSandboxExecution command = new CreateSandboxExecution(
        submission.sessionId(),
        submission.turnIndex(),
        SandboxWorkloadType.PATCH,
        null,
        target.scenarioId(),
        target.workspaceRef(),
        target.testsRef(),
        submission.language(),
        null,
        null,
        SandboxRunMode.FULL
    );
    StoredAlgorithmSource source = sourceStorage.store(
        submission.sessionId(), submission.language(), submission.patch());
    SandboxExecution execution = sandboxPersistenceService.createOrReuse(
        command.withSource(source.codeRef(), source.codeHash())
    );
    if (execution.status() != SandboxExecutionStatus.PENDING) {
      return execution;
    }
    if (!producer.sendExecution(execution.id())) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "PATCH 沙箱任务入队失败");
    }
    return execution;
  }
}
