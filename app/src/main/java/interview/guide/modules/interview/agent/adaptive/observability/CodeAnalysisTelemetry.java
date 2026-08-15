package interview.guide.modules.interview.agent.adaptive.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CodeAnalysisTelemetry {

  private static final String JOBS = "app.interview.adaptive.code-analysis.jobs";
  private static final String ANCHORS = "app.interview.adaptive.code-analysis.anchors";
  private static final String DURATION = "app.interview.adaptive.code-analysis.duration";
  private static final String TOKEN_COST = "app.interview.adaptive.code-analysis.token-cost";

  private final MeterRegistry meterRegistry;

  public void jobSubmitted() {
    meterRegistry.counter(JOBS, "status", "submitted").increment();
  }

  public void jobCompleted(long durationMs, long tokenCost) {
    meterRegistry.counter(JOBS, "status", "completed").increment();
    meterRegistry.timer(DURATION).record(Duration.ofMillis(durationMs));
    meterRegistry.summary(TOKEN_COST).record(tokenCost);
  }

  public void jobsTimedOut(int count) {
    meterRegistry.counter(JOBS, "status", "timed_out").increment(count);
  }

  public void anchorsAccepted(int count) {
    meterRegistry.counter(ANCHORS, "outcome", "accepted").increment(count);
  }

  public void anchorRejected() {
    meterRegistry.counter(ANCHORS, "outcome", "rejected").increment();
  }
}
