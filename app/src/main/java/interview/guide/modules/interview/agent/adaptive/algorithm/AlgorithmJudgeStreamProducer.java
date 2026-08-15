package interview.guide.modules.interview.agent.adaptive.algorithm;

import interview.guide.common.async.AbstractStreamProducer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.infrastructure.redis.RedisService;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AlgorithmJudgeStreamProducer
    extends AbstractStreamProducer<AlgorithmJudgeStreamProducer.ExecutionTask> {

  record ExecutionTask(String executionId, int retryCount) {}

  AlgorithmJudgeStreamProducer(RedisService redisService) {
    super(redisService);
  }

  public boolean sendExecution(String executionId) {
    return sendTask(new ExecutionTask(executionId, 0));
  }

  boolean sendExecution(String executionId, int retryCount) {
    return sendTask(new ExecutionTask(executionId, retryCount));
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
  protected Map<String, String> buildMessage(ExecutionTask payload) {
    return Map.of(
        AsyncTaskStreamConstants.FIELD_EXECUTION_ID, payload.executionId(),
        AsyncTaskStreamConstants.FIELD_RETRY_COUNT, Integer.toString(payload.retryCount())
    );
  }

  @Override
  protected String payloadIdentifier(ExecutionTask payload) {
    return payload.executionId();
  }

  @Override
  protected void onSendFailed(ExecutionTask payload, String error) {
    // 调用方根据 sendTask 的 false 结果快速失败。
  }
}
