package interview.guide.modules.interview.agent.adaptive.algorithm.judge;

import interview.guide.common.async.AbstractStreamConsumer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmInterviewProperties;
import interview.guide.modules.interview.agent.adaptive.algorithm.problem.AlgorithmProblem;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionResult;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionSpec;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxVerdict;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxWorker;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxWorkloadType;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import java.util.Map;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

/**
 * 算法判题结果流消费者，消费沙箱评测结果并更新执行状态。
 */
@Component
class AlgorithmJudgeStreamConsumer
    extends AbstractStreamConsumer<AlgorithmJudgeStreamConsumer.ExecutionTask> {

  record ExecutionTask(String executionId) {}

  private final AlgorithmPersistenceService persistenceService;
  private final SandboxWorker sandboxWorker;
  private final AlgorithmJudgeStreamProducer producer;
  private final AlgorithmResultReadyHandler resultReadyHandler;
  private final AlgorithmInterviewTelemetry telemetry;
  private final AlgorithmInterviewProperties properties;

  AlgorithmJudgeStreamConsumer(
      RedisService redisService,
      AlgorithmPersistenceService persistenceService,
      SandboxWorker sandboxWorker,
      AlgorithmJudgeStreamProducer producer,
      AlgorithmResultReadyHandler resultReadyHandler,
      AlgorithmInterviewTelemetry telemetry,
      AlgorithmInterviewProperties properties
  ) {
    super(redisService);
    this.persistenceService = persistenceService;
    this.sandboxWorker = sandboxWorker;
    this.producer = producer;
    this.resultReadyHandler = resultReadyHandler;
    this.telemetry = telemetry;
    this.properties = properties;
  }

  @Override
  protected String taskDisplayName() {
    return "算法判题";
  }

  @Override
  protected String streamKey() {
    return AsyncTaskStreamConstants.ALGORITHM_JUDGE_STREAM_KEY;
  }

  @Override
  protected String groupName() {
    return AsyncTaskStreamConstants.ALGORITHM_JUDGE_GROUP_NAME;
  }

  @Override
  protected String consumerPrefix() {
    return AsyncTaskStreamConstants.ALGORITHM_JUDGE_CONSUMER_PREFIX;
  }

  @Override
  protected String threadName() {
    return "algorithm-judge-consumer";
  }

  @Override
  protected ExecutionTask parsePayload(
      StreamMessageId messageId,
      Map<String, String> data
  ) {
    String executionId = data.get(AsyncTaskStreamConstants.FIELD_EXECUTION_ID);
    return executionId == null ? null : new ExecutionTask(executionId);
  }

  @Override
  protected String payloadIdentifier(ExecutionTask payload) {
    return payload.executionId();
  }

  @Override
  protected boolean shouldSkip(ExecutionTask payload) {
    return !persistenceService.executionExists(payload.executionId());
  }

  @Override
  protected void markProcessing(ExecutionTask payload) {
    // 判题任务通过 tryMarkProcessing 原子领取。
  }

  @Override
  protected boolean tryMarkProcessing(ExecutionTask payload) {
    return persistenceService.markRunning(payload.executionId());
  }

  @Override
  protected void processBusiness(ExecutionTask payload) {
    SandboxExecution execution = persistenceService.getExecution(payload.executionId());
    SandboxExecutionResult result = sandboxWorker.execute(execution, executionSpec(execution));
    telemetry.attemptCompleted(
        execution.id(),
        execution.sessionId(),
        result.verdict(),
        result.policyViolation()
    );
    boolean retry = persistenceService.applyResult(execution.id(), result);
    if (retry) {
      if (!producer.sendExecution(execution.id())) {
        throw new IllegalStateException("IE rejudge task enqueue failed");
      }
      return;
    }
    try {
      resultReadyHandler.handle(persistenceService.getExecution(execution.id()));
    } catch (Exception e) {
      telemetry.resultReadyFailed();
      throw new IllegalStateException(
          "判题结果已落库但唤醒编排器失败 executionId=" + execution.id(), e);
    }
  }

  private SandboxExecutionSpec executionSpec(SandboxExecution execution) {
    if (execution.workloadType() == SandboxWorkloadType.PATCH) {
      return new SandboxExecutionSpec(
          execution.scenarioId(),
          execution.testsRef(),
          execution.workspaceRef(),
          properties.getPatchTimeLimitMs(),
          properties.getPatchMemoryLimitKb()
      );
    }
    AlgorithmProblem problem = persistenceService.getProblem(execution.problemId());
    return new SandboxExecutionSpec(
        problem.id(),
        execution.runMode() == SandboxRunMode.SAMPLE
            ? problem.sampleCasesRef()
            : problem.hiddenCasesRef(),
        null,
        problem.timeLimitMs(),
        problem.memoryLimitKb()
    );
  }

  @Override
  protected void markCompleted(ExecutionTask payload) {
    // 判题结果已由 processBusiness 的短事务完整写入。
  }

  @Override
  protected void markFailed(ExecutionTask payload, String error) {
    SandboxExecution execution = persistenceService.markInfrastructureFailure(
        payload.executionId()
    );
    telemetry.attemptCompleted(SandboxVerdict.IE);
    try {
      resultReadyHandler.handle(execution);
    } catch (Exception e) {
      telemetry.resultReadyFailed();
      throw new IllegalStateException(
          "降级唤醒编排器失败 executionId=" + payload.executionId(), e);
    }
  }

  @Override
  protected void retryMessage(ExecutionTask payload, int retryCount) {
    if (!persistenceService.resetAfterWorkerFailure(payload.executionId())) {
      return;
    }
    if (!producer.sendExecution(payload.executionId(), retryCount)) {
      persistenceService.markInfrastructureFailure(payload.executionId());
    }
  }
}
