package interview.guide.modules.interview.agent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提交 Agent 面试回答请求。
 */
public record SubmitAgentAnswerRequest(
    @NotBlank(message = "回答不能为空")
    @Size(max = 20000, message = "回答最多 20000 字符")
    String answer
) {}
