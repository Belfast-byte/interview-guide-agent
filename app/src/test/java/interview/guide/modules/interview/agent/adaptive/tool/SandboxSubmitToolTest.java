package interview.guide.modules.interview.agent.adaptive.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmSubmissionService;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxExecutionStatus;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.algorithm.SubmitAlgorithmCode;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.CandidateCodeSubmission;
import interview.guide.modules.interview.agent.adaptive.core.InterviewerContext;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodePatchSubmissionService;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SandboxSubmitToolTest {

  @Mock
  private AlgorithmSubmissionService submissionService;

  @Mock
  private CodePatchSubmissionService patchSubmissionService;

  @Mock
  private ReActRequest request;

  @Mock
  private InterviewerContext context;

  @Test
  @DisplayName("沙箱工具只提交编排器绑定的候选人源码原文")
  void shouldSubmitExactCandidateCode() {
    CandidateAnswer answer = new CandidateAnswer(
        2,
        "class Main { public static void main(String[] args) {} }",
        new CandidateCodeSubmission("two-sum", "JAVA", "FULL")
    );
    when(request.sessionId()).thenReturn("session-1");
    when(request.interviewerContext()).thenReturn(context);
    when(context.currentCodeSubmission()).thenReturn(answer);
    when(submissionService.submit(any())).thenReturn(pendingExecution());
    SandboxSubmitTool tool = new SandboxSubmitTool(submissionService, patchSubmissionService);

    PendingToolResult result = (PendingToolResult) tool.execute(
        request,
        Map.of("problemId", "two-sum", "runMode", "FULL")
    );

    ArgumentCaptor<SubmitAlgorithmCode> command = ArgumentCaptor.forClass(
        SubmitAlgorithmCode.class
    );
    verify(submissionService).submit(command.capture());
    assertThat(command.getValue().source()).isEqualTo(answer.content());
    assertThat(command.getValue().sessionId()).isEqualTo("session-1");
    assertThat(command.getValue().turnIndex()).isEqualTo(2);
    assertThat(result.handle()).isEqualTo("execution-1");
    assertThat(result.targetTurnIndex()).isEqualTo(2);
  }

  @Test
  @DisplayName("模型篡改题目标识或运行模式时快速失败")
  void shouldRejectArgumentsThatDoNotMatchCandidateSubmission() {
    when(request.interviewerContext()).thenReturn(context);
    when(context.currentCodeSubmission()).thenReturn(new CandidateAnswer(
        2,
        "source",
        new CandidateCodeSubmission("two-sum", "JAVA", "FULL")
    ));
    SandboxSubmitTool tool = new SandboxSubmitTool(submissionService, patchSubmissionService);

    assertThatThrownBy(() -> tool.execute(
        request,
        Map.of("problemId", "another-problem", "runMode", "FULL")
    )).isInstanceOf(BusinessException.class)
        .hasMessageContaining("do not match");
  }

  @Test
  @DisplayName("PATCH 场景只提交编排器绑定的候选人补丁原文")
  void shouldSubmitExactCandidatePatch() {
    CandidateAnswer answer = new CandidateAnswer(
        3,
        "@@ -1 +1 @@\n-old\n+new",
        new CandidateCodeSubmission(null, "scenario-1", "JAVA", "FULL")
    );
    when(request.sessionId()).thenReturn("session-1");
    when(request.interviewerContext()).thenReturn(context);
    when(context.currentCodeSubmission()).thenReturn(answer);
    when(patchSubmissionService.submit(
        "session-1",
        3,
        "scenario-1",
        SandboxLanguage.JAVA,
        answer.content()
    )).thenReturn(pendingExecution());
    SandboxSubmitTool tool = new SandboxSubmitTool(submissionService, patchSubmissionService);

    PendingToolResult result = (PendingToolResult) tool.execute(
        request,
        Map.of("scenarioId", "scenario-1", "runMode", "FULL")
    );

    assertThat(result.handle()).isEqualTo("execution-1");
    verify(patchSubmissionService).submit(
        "session-1",
        3,
        "scenario-1",
        SandboxLanguage.JAVA,
        answer.content()
    );
  }

  private SandboxExecution pendingExecution() {
    return new SandboxExecution(
        "execution-1", "session-1", 10L, 1, "two-sum",
        SandboxLanguage.JAVA, "source-ref", "a".repeat(64), SandboxRunMode.FULL,
        SandboxExecutionStatus.PENDING, null, null, null, null, null, null,
        null, false, 0, LocalDateTime.now(), null
    );
  }
}
