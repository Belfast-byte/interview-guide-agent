package interview.guide.modules.interview.agent.adaptive.api;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.security.AuthenticatedUser;
import interview.guide.modules.auth.domain.UserRole;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.api.SubmitAdaptiveAnswerRequest.CandidateCodeSubmissionRequest;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewAnswerExecutor;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewApplicationService;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewHistoryService;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewSummary;
import interview.guide.modules.interview.agent.adaptive.application.CandidateInterviewCreationCommand;
import interview.guide.modules.interview.agent.adaptive.application.InterviewCreationEventSink;
import interview.guide.modules.interview.agent.adaptive.assessment.report.AssessmentReportService;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("自适应面试 Controller 测试")
class AdaptiveInterviewControllerTest {

  @Mock private AdaptiveInterviewApplicationService applicationService;
  @Mock private AdaptiveInterviewHistoryService historyService;
  @Mock private AssessmentReportService reportService;
  @Mock private AdaptiveInterviewAnswerExecutor answerExecutor;
  @InjectMocks private AdaptiveInterviewController controller;

  @Test
  @DisplayName("嵌套请求保留 JSON 结构及级联校验")
  void nestedRequestsKeepJsonAndValidationContract() {
    var mapper = new tools.jackson.databind.ObjectMapper();
    var request = mapper.readValue("""
        {"jd":"JD","resume":"简历","providerId":null,"mode":"PRACTICE",
         "candidateLevel":"EXPERIENCED","practiceScope":[{"skillId":"java-backend","focusId":""}]}
        """, CreateAdaptiveInterviewRequest.class);
    assertThat(request.practiceScope().getFirst().skillId()).isEqualTo("java-backend");
    try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
      assertThat(factory.getValidator().validate(request))
          .extracting(v -> v.getPropertyPath().toString())
          .containsExactly("practiceScope[0].focusId");
      var answer = mapper.readValue("""
          {"turnIndex":1,"answer":"代码","codeSubmission":{
           "problemId":"two-sum","scenarioId":null,"language":null,"runMode":"FULL"}}
          """, SubmitAdaptiveAnswerRequest.class);
      assertThat(factory.getValidator().validate(answer))
          .extracting(v -> v.getPropertyPath().toString())
          .contains("codeSubmission.language");
      assertThat(mapper.valueToTree(answer).path("codeSubmission").path("problemId").asText())
          .isEqualTo("two-sum");
    }
  }

  @Test
  @DisplayName("历史列表只使用认证主体中的候选人 ID")
  void historyUsesAuthenticatedCandidateId() {
    UUID candidateId = UUID.randomUUID();
    AuthenticatedUser principal = new AuthenticatedUser(candidateId, UserRole.CANDIDATE);
    AdaptiveInterviewSummary summary = new AdaptiveInterviewSummary(
        "session-1",
        AdaptiveSessionStatus.IN_PROGRESS,
        2,
        6,
        "Java 后端工程师",
        LocalDateTime.of(2026, 8, 22, 10, 0),
        null
    );
    PageRequest pageable = PageRequest.of(0, AdaptiveInterviewHistoryService.PAGE_SIZE);
    when(historyService.list(candidateId, 0))
        .thenReturn(new PageImpl<>(List.of(summary), pageable, 1));

    var response = controller.history(principal, 0).getData();

    assertThat(response.content()).singleElement().satisfies(item -> {
      assertThat(item.sessionId()).isEqualTo("session-1");
      assertThat(item.jdSummary()).isEqualTo("Java 后端工程师");
    });
    verify(historyService).list(candidateId, 0);
  }

  @Test
  @DisplayName("流式答题接口拒绝代码提交回答")
  void streamAnswerRejectsCodeSubmission() {
    AuthenticatedUser principal = new AuthenticatedUser(UUID.randomUUID(), UserRole.CANDIDATE);
    SubmitAdaptiveAnswerRequest request = new SubmitAdaptiveAnswerRequest(
        1,
        "class Main {}",
        new CandidateCodeSubmissionRequest(
            "two-sum",
            null,
            SandboxLanguage.JAVA,
            SandboxRunMode.FULL
        ), null
    );

    assertThatThrownBy(() -> controller.submitAnswerStream("session-1", principal, request))
        .isInstanceOf(BusinessException.class)
        .hasMessage("代码提交回答请使用同步接口");
    verifyNoInteractions(applicationService, answerExecutor);
  }

  @Test
  @DisplayName("重试使用认证候选人与服务器保存的回答，并在排队前建立 deadline")
  void retryUsesAuthenticatedCandidateAndQueuedDeadline() {
    UUID candidateId = UUID.randomUUID();
    AuthenticatedUser principal = new AuthenticatedUser(candidateId, UserRole.CANDIDATE);
    long started = System.nanoTime();
    var emitter = controller.retryAnswerStream("session-1", 1, principal);
    assertThat(emitter.getTimeout()).isEqualTo(75_000L);
    var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
    verify(answerExecutor).execute(task.capture());
    long queued = System.nanoTime();
    when(applicationService.retryAnswerStreaming(any(), any(), org.mockito.ArgumentMatchers.eq(1), any()))
        .thenThrow(new BusinessException(interview.guide.common.exception.ErrorCode.AI_SERVICE_TIMEOUT, "超时"));
    task.getValue().run();
    var sink = org.mockito.ArgumentCaptor.forClass(
        interview.guide.modules.interview.agent.adaptive.application.AnswerEventSink.class);
    verify(applicationService).retryAnswerStreaming(
        org.mockito.ArgumentMatchers.eq(candidateId.toString()), org.mockito.ArgumentMatchers.eq("session-1"),
        org.mockito.ArgumentMatchers.eq(1), sink.capture());
    assertThat(sink.getValue().deadlineNanos()).isBetween(
        started + java.time.Duration.ofSeconds(60).toNanos(),
        queued + java.time.Duration.ofSeconds(60).toNanos());
  }

  @Test
  @DisplayName("流式创建只使用认证主体并注册创建事件 sink")
  void streamCreationUsesAuthenticatedCandidate() {
    UUID candidateId = UUID.randomUUID();
    AuthenticatedUser principal = new AuthenticatedUser(candidateId, UserRole.CANDIDATE);
    CreateAdaptiveInterviewRequest request = new CreateAdaptiveInterviewRequest(
        "JD",
        "Resume",
        "provider-1",
        EVALUATION_SETTINGS.mode(),
        EVALUATION_SETTINGS.candidateLevel(),
        List.of()
    );

    assertThat(controller.createStream(principal, request)).isNotNull();

    verify(applicationService).createForCandidateStreaming(
        argThat((CandidateInterviewCreationCommand command) ->
            command.candidateId().equals(candidateId)
                && command.requestedProviderId().equals("provider-1")
                && command.settings().equals(EVALUATION_SETTINGS)),
        any(InterviewCreationEventSink.class)
    );
  }
}
