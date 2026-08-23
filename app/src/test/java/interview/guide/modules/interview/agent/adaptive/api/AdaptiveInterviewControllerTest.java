package interview.guide.modules.interview.agent.adaptive.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.security.AuthenticatedUser;
import interview.guide.modules.auth.domain.UserRole;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewAnswerExecutor;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewApplicationService;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewHistoryService;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewSummary;
import interview.guide.modules.interview.agent.adaptive.application.CandidateInterviewCreationCommand;
import interview.guide.modules.interview.agent.adaptive.application.InterviewCreationEventSink;
import interview.guide.modules.interview.agent.adaptive.assessment.report.AssessmentReportService;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.memory.profile.CandidateAbilityProfileService;
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
  @Mock private CandidateAbilityProfileService abilityProfileService;
  @Mock private AdaptiveInterviewAnswerExecutor answerExecutor;
  @InjectMocks private AdaptiveInterviewController controller;

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
        )
    );

    assertThatThrownBy(() -> controller.submitAnswerStream("session-1", principal, request))
        .isInstanceOf(BusinessException.class)
        .hasMessage("代码提交回答请使用同步接口");
    verifyNoInteractions(applicationService, answerExecutor);
  }

  @Test
  @DisplayName("流式创建只使用认证主体并注册创建事件 sink")
  void streamCreationUsesAuthenticatedCandidate() {
    UUID candidateId = UUID.randomUUID();
    AuthenticatedUser principal = new AuthenticatedUser(candidateId, UserRole.CANDIDATE);
    CreateAdaptiveInterviewRequest request = new CreateAdaptiveInterviewRequest(
        "JD",
        "Resume",
        "provider-1"
    );

    assertThat(controller.createStream(principal, request)).isNotNull();

    verify(applicationService).createForCandidateStreaming(
        argThat((CandidateInterviewCreationCommand command) ->
            command.candidateId().equals(candidateId)
                && command.requestedProviderId().equals("provider-1")),
        any(InterviewCreationEventSink.class)
    );
  }
}
