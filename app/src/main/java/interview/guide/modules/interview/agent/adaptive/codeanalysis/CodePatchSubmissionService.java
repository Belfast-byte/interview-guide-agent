package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmJudgeStreamProducer;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmPersistenceService;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmSourceStorage;
import interview.guide.modules.interview.agent.adaptive.algorithm.CreateSandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxWorkloadType;
import interview.guide.modules.interview.agent.adaptive.algorithm.StoredAlgorithmSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CodePatchSubmissionService {

  private final CodeAnalysisPersistenceService codeAnalysisPersistenceService;
  private final AlgorithmSourceStorage sourceStorage;
  private final AlgorithmPersistenceService sandboxPersistenceService;
  private final AlgorithmJudgeStreamProducer producer;

  public SandboxExecution submit(
      String sessionId,
      int turnIndex,
      String scenarioId,
      SandboxLanguage language,
      String patch
  ) {
    PatchScenarioTarget target = codeAnalysisPersistenceService.getPatchTarget(
        sessionId,
        scenarioId
    );
    if (target.testsRef() == null || target.testsRef().isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "PATCH 场景缺少预置测试");
    }
    StoredAlgorithmSource source = sourceStorage.store(sessionId, language, patch);
    SandboxExecution execution = sandboxPersistenceService.createPending(
        new CreateSandboxExecution(
            sessionId,
            turnIndex,
            SandboxWorkloadType.PATCH,
            null,
            target.scenarioId(),
            target.workspaceRef(),
            target.testsRef(),
            language,
            source.codeRef(),
            source.codeHash(),
            SandboxRunMode.FULL
        )
    );
    if (!producer.sendExecution(execution.id())) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "PATCH 沙箱任务入队失败");
    }
    return execution;
  }
}
