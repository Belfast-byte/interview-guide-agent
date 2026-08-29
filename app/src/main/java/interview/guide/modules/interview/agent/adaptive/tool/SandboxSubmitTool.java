package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.algorithm.judge.AlgorithmSubmissionService;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.algorithm.judge.SubmitAlgorithmCode;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario.CodePatchSubmissionService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario.PatchCodeSubmission;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateCodeSubmission;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * 代码提交工具，将候选人代码提交到沙箱评测。
 */
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
    callback = ToolCallbacks.gatewayOnly(
        NAME,
        "Submit the candidate's exact current code answer for asynchronous judging",
        SandboxSubmitInput.class
    );
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
  public void validate(ReActRequest request, Map<String, Object> arguments) {
    CandidateCodeSubmission submission = submission(request);
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
  }

  @Override
  public ToolResult execute(
      ReActRequest request,
      Map<String, Object> arguments,
      String idempotencyKey
  ) {
    validate(request, arguments);
    CandidateAnswer answer = request.interviewerContext().currentCodeSubmission();
    CandidateCodeSubmission submission = answer.codeSubmission();
    SandboxExecution execution = submission.patch()
        ? patchSubmissionService.submit(new PatchCodeSubmission(
            request.sessionId(),
            answer.turnIndex(),
            submission.scenarioId(),
            SandboxLanguage.valueOf(submission.language()),
            answer.content()
        ))
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

  private CandidateCodeSubmission submission(ReActRequest request) {
    CandidateAnswer answer = request.interviewerContext().currentCodeSubmission();
    if (answer == null) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "sandbox_submit requires a candidate code submission"
      );
    }
    return answer.codeSubmission();
  }

  record SandboxSubmitInput(String problemId, String scenarioId, String runMode) {}

  record SandboxPendingResult(String submissionId, String status) {}
}
