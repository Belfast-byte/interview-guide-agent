package interview.guide.modules.interview.agent.adaptive.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmEvidenceConsumer;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionStatus;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxVerdict;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxWorkloadType;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdaptiveAlgorithmResultReadyHandlerTest {

  @Mock
  private AlgorithmEvidenceConsumer evidenceConsumer;

  @Mock
  private AlgorithmInterviewTelemetry telemetry;

  @Test
  @DisplayName("终态执行直接交给 Evidence 消费器")
  void shouldConsumeTerminalExecution() {
    SandboxExecution execution = execution();
    when(evidenceConsumer.consume("execution-1")).thenReturn(true);

    new AdaptiveAlgorithmResultReadyHandler(evidenceConsumer, telemetry).handle(execution);

    verify(telemetry).resultReady(execution);
    verify(evidenceConsumer).consume("execution-1");
  }

  @Test
  @DisplayName("重复消费只记录幂等指标")
  void shouldRecordDuplicateConsumption() {
    when(evidenceConsumer.consume("execution-1")).thenReturn(false);

    new AdaptiveAlgorithmResultReadyHandler(evidenceConsumer, telemetry).handle(execution());

    verify(telemetry).resultReadyDeduped();
  }

  private SandboxExecution execution() {
    return new SandboxExecution(
        "execution-1", "session-1", 10L, 1, SandboxWorkloadType.ALGORITHM,
        "two-sum", null, null, null,
        SandboxLanguage.JAVA, "source-ref", "a".repeat(64), SandboxRunMode.FULL,
        SandboxExecutionStatus.DONE, SandboxVerdict.WA, 4, 10, 120L, 32_768L, 7,
        null, LocalDateTime.now().minusSeconds(1), LocalDateTime.now(), null
    );
  }
}
