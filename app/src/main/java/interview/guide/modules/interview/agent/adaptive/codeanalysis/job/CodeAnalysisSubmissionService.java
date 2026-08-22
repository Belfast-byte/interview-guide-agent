package interview.guide.modules.interview.agent.adaptive.codeanalysis.job;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import interview.guide.modules.interview.agent.adaptive.observability.CodeAnalysisTelemetry;
import org.springframework.stereotype.Service;

/**
 * 代码分析提交服务，创建分析任务并投递到工作队列。
 */
@Service
@RequiredArgsConstructor
public class CodeAnalysisSubmissionService {

  private final CodeAnalysisPersistenceService persistenceService;
  private final CodeAnalysisStreamProducer producer;
  private final CodeAnalysisTelemetry telemetry;

  public CodeAnalysisJob submit(
      String sessionId,
      String tenantId,
      String repositoryRef,
      String commitHash,
      LocalDateTime expiresAt
  ) {
    CodeAnalysisJob job = persistenceService.createJob(
        sessionId,
        tenantId,
        repositoryRef,
        commitHash,
        expiresAt
    );
    if (job.status() == AnalysisJobStatus.PENDING && !deliver(job.id())) {
      persistenceService.markFailed(job.id(), "代码分析任务投递失败");
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "代码分析任务投递失败");
    }
    telemetry.jobSubmitted();
    return job;
  }

  /**
   * 投递代码分析任务：首次失败时同步重投一次，仍失败才视为投递失败。
   * 仓库内没有该 Stream 的消费者，任务由外部 worker 消费，重投只为吸收瞬时 Redis 抖动。
   */
  private boolean deliver(String jobId) {
    return producer.send(jobId) || producer.send(jobId);
  }
}
