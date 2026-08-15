package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmSubmissionService;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.algorithm.SubmitAlgorithmCode;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.CandidateCodeSubmission;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodePatchSubmissionService;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

@Component
public class SandboxSubmitTool implements AdaptiveAgentTool {

  public static final String NAME = "sandbox_submit";

  private final AlgorithmSubmissionService submissionService;
  private final CodePatchSubmissionService patchSubmissionService;
  private final ToolCallback callback;

  public SandboxSubmitTool(
      AlgorithmSubmissionService submissionService,
      CodePatchSubmissionService patchSubmissionService
  ) {
    this.submissionService = submissionService;
    this.patchSubmissionService = patchSubmissionService;
    callback = FunctionToolCallback
        .builder(NAME, (SandboxSubmitInput input) -> unsupportedDirectCall())
        .description("Submit the candidate's exact current code answer for asynchronous judging")
        .inputType(SandboxSubmitInput.class)
        .build();
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public ToolCallback callback() {
    return callback;
  }

  @Override
  public ToolResult execute(ReActRequest request, Map<String, Object> arguments) {
    CandidateAnswer answer = request.interviewerContext().currentCodeSubmission();
    if (answer == null) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "sandbox_submit requires a candidate code submission"
      );
    }
    CandidateCodeSubmission submission = answer.codeSubmission();
    String runMode = ToolArguments.requiredString(arguments, "runMode", 16);
    String targetArgument = submission.patch() ? "scenarioId" : "problemId";
    String targetId = ToolArguments.requiredString(arguments, targetArgument, 64);
    String expectedTargetId = submission.patch()
        ? submission.scenarioId()
        : submission.problemId();
    if (!targetId.equals(expectedTargetId) || !runMode.equals(submission.runMode())) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "sandbox_submit arguments do not match the candidate submission"
      );
    }
    SandboxExecution execution = submission.patch()
        ? patchSubmissionService.submit(
            request.sessionId(),
            answer.turnIndex(),
            submission.scenarioId(),
            SandboxLanguage.valueOf(submission.language()),
            answer.content()
        )
        : submissionService.submit(new SubmitAlgorithmCode(
            request.sessionId(),
            answer.turnIndex(),
            submission.problemId(),
            SandboxLanguage.valueOf(submission.language()),
            answer.content(),
            SandboxRunMode.valueOf(submission.runMode())
        ));
    return new PendingToolResult(
        execution.id(),
        new SandboxPendingResult(execution.id(), execution.status().name()),
        "submissionId=" + execution.id() + ", status=" + execution.status(),
        answer.turnIndex()
    );
  }

  @Override
  public ToolResult execute(Map<String, Object> arguments) {
    throw new BusinessException(
        ErrorCode.AI_SERVICE_ERROR,
        "sandbox_submit requires interview context"
    );
  }

  private String unsupportedDirectCall() {
    throw new IllegalStateException("Tool execution must go through ToolGateway");
  }

  record SandboxSubmitInput(String problemId, String scenarioId, String runMode) {}

  record SandboxPendingResult(String submissionId, String status) {}
}
