package interview.guide.modules.interview.agent.adaptive.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmSessionFacts;
import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmAssessmentEvidenceService;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionStatus;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxVerdict;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxWorkloadType;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdaptiveAlgorithmResultReadyHandlerTest {

  @Mock
  private AdaptiveInterviewApplicationService applicationService;

  @Mock
  private AlgorithmSessionFacts sessionFacts;

  @Mock
  private AlgorithmAssessmentEvidenceService assessmentEvidenceService;

  @Mock
  private AlgorithmInterviewTelemetry telemetry;

  @Test
  @DisplayName("过期代码结果落库后不唤醒面试官")
  void shouldIgnoreSupersededResult() {
    AdaptiveAlgorithmResultReadyHandler handler = handler();

    handler.handle(execution("execution-2", SandboxExecutionStatus.DONE));

    verify(applicationService, never()).handleToolResult(
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any()
    );
  }

  @Test
  @DisplayName("有效判题结果只注入摘要，不包含代码或隐藏用例")
  void shouldPublishSafeResultSummary() {
    AdaptiveAlgorithmResultReadyHandler handler = handler();
    when(sessionFacts.turnIndex(10L)).thenReturn(1);
    when(applicationService.reserveToolResultEvent(
        org.mockito.ArgumentMatchers.eq("session-1"),
        org.mockito.ArgumentMatchers.any()
    )).thenReturn(true);
    ArgumentCaptor<ToolResultEvent> event = ArgumentCaptor.forClass(ToolResultEvent.class);

    handler.handle(execution(null, SandboxExecutionStatus.DONE));

    verify(applicationService).handleToolResult(
        org.mockito.ArgumentMatchers.eq("session-1"),
        event.capture()
    );
    verify(applicationService).reassessAlgorithmResult(
        "session-1",
        1,
        event.getValue().output()
    );
    assertThat(event.getValue().output())
        .contains("verdict=WA", "passed=4/10", "firstFailedCase=7")
        .doesNotContain("source-ref", "hidden");
  }

  @Test
  @DisplayName("重复投递在预留阶段被幂等拒绝，不触发任何 LLM 重评")
  void shouldSkipDuplicateDeliveryBeforeReassessment() {
    AdaptiveAlgorithmResultReadyHandler handler = handler();
    when(sessionFacts.turnIndex(10L)).thenReturn(1);
    when(applicationService.reserveToolResultEvent(
        org.mockito.ArgumentMatchers.eq("session-1"),
        org.mockito.ArgumentMatchers.any()
    )).thenReturn(false);

    handler.handle(execution(null, SandboxExecutionStatus.DONE));

    verify(applicationService, never()).reassessAlgorithmResult(
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyInt(),
        org.mockito.ArgumentMatchers.anyString()
    );
    verify(applicationService, never()).handleToolResult(
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any()
    );
    verify(telemetry).resultReadyDeduped();
  }

  @Test
  @DisplayName("排队超时摘要客观陈述判题不可用，不带评审立场")
  void shouldPublishNeutralTimeoutSummary() {
    AdaptiveAlgorithmResultReadyHandler handler = handler();
    when(sessionFacts.turnIndex(10L)).thenReturn(1);
    when(applicationService.reserveToolResultEvent(
        org.mockito.ArgumentMatchers.eq("session-1"),
        org.mockito.ArgumentMatchers.any()
    )).thenReturn(true);
    ArgumentCaptor<ToolResultEvent> event = ArgumentCaptor.forClass(ToolResultEvent.class);

    handler.handle(execution(null, SandboxExecutionStatus.TIMEOUT_QUEUED));

    verify(applicationService).handleToolResult(
        org.mockito.ArgumentMatchers.eq("session-1"),
        event.capture()
    );
    verify(applicationService, never()).reassessAlgorithmResult(
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyInt(),
        org.mockito.ArgumentMatchers.anyString()
    );
    assertThat(event.getValue().output())
        .contains("status=TIMEOUT_QUEUED, judging unavailable")
        .doesNotContain("negative evidence");
  }

  @Test
  @DisplayName("预留成功后处理抛运行时异常时回滚预留并原样抛出")
  void shouldRollbackReservationWhenProcessingFailsWithRuntimeException() {
    AdaptiveAlgorithmResultReadyHandler handler = handler();
    when(sessionFacts.turnIndex(10L)).thenReturn(1);
    when(applicationService.reserveToolResultEvent(eq("session-1"), any()))
        .thenReturn(true);
    RuntimeException failure = new RuntimeException("database unavailable");
    doThrow(failure)
        .when(applicationService)
        .reassessAlgorithmResult(eq("session-1"), eq(1), anyString());

    assertThatThrownBy(() -> handler.handle(execution(null, SandboxExecutionStatus.DONE)))
        .isSameAs(failure);

    ArgumentCaptor<ToolResultEvent> event = ArgumentCaptor.forClass(ToolResultEvent.class);
    verify(applicationService).discardToolResultReservation(event.capture());
    assertThat(event.getValue().resultId()).isEqualTo("execution-1");
  }

  private AdaptiveAlgorithmResultReadyHandler handler() {
    return new AdaptiveAlgorithmResultReadyHandler(
        applicationService,
        sessionFacts,
        assessmentEvidenceService,
        telemetry
    );
  }

  private SandboxExecution execution(
      String supersededBy,
      SandboxExecutionStatus status
  ) {
    return new SandboxExecution(
        "execution-1", "session-1", 10L, 1, SandboxWorkloadType.ALGORITHM,
        "two-sum", null, null, null,
        SandboxLanguage.JAVA, "source-ref", "a".repeat(64), SandboxRunMode.FULL,
        status, SandboxVerdict.WA, 4, 10, 120L, 32_768L, 7,
        supersededBy,
        LocalDateTime.now().minusSeconds(1), LocalDateTime.now(), null
    );
  }
}
