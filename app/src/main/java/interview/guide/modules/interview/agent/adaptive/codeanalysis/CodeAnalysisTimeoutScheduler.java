package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import interview.guide.modules.interview.agent.adaptive.observability.CodeAnalysisTelemetry;

@Component
@RequiredArgsConstructor
public class CodeAnalysisTimeoutScheduler {

  private final CodeAnalysisPersistenceService persistenceService;
  private final CodeAnalysisProperties properties;
  private final CodeAnalysisTelemetry telemetry;

  @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
  public void timeoutStaleJobs() {
    int timedOut = persistenceService.timeoutCreatedBefore(
        LocalDateTime.now().minus(properties.getTimeout())
    );
    telemetry.jobsTimedOut(timedOut);
  }
}
