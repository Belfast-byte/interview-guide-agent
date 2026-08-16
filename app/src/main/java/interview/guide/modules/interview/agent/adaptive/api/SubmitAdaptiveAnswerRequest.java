package interview.guide.modules.interview.agent.adaptive.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;

/**
 * 提交自适应面试回答请求。
 */
public record SubmitAdaptiveAnswerRequest(
    @Min(value = 1, message = "轮次必须从 1 开始") int turnIndex,
    @NotBlank(message = "回答不能为空") String answer,
    @Valid CandidateCodeSubmissionRequest codeSubmission
) {}
