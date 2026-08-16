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
    if (job.status() == AnalysisJobStatus.PENDING && !producer.send(job.id())) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "代码分析任务投递失败");
    }
    telemetry.jobSubmitted();
    return job;
  }
}
