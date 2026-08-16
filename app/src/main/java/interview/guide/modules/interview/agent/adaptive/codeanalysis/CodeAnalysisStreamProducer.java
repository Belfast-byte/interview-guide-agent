package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import interview.guide.common.async.AbstractStreamProducer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.infrastructure.redis.RedisService;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 代码分析任务流生产者。
 */
@Component
class CodeAnalysisStreamProducer
    extends AbstractStreamProducer<CodeAnalysisStreamProducer.AnalysisTask> {

  record AnalysisTask(String jobId) {}

  CodeAnalysisStreamProducer(RedisService redisService) {
    super(redisService);
  }

  boolean send(String jobId) {
    return sendTask(new AnalysisTask(jobId));
  }

  @Override
  protected String taskDisplayName() {
    return "项目代码分析";
  }

  @Override
  protected String streamKey() {
    return AsyncTaskStreamConstants.CODE_ANALYSIS_STREAM_KEY;
  }

  @Override
  protected Map<String, String> buildMessage(AnalysisTask payload) {
    return Map.of(AsyncTaskStreamConstants.FIELD_ANALYSIS_JOB_ID, payload.jobId());
  }

  @Override
  protected String payloadIdentifier(AnalysisTask payload) {
    return payload.jobId();
  }

  @Override
  protected void onSendFailed(AnalysisTask payload, String error) {
    // 调用方根据 false 结果快速失败。
  }
}
