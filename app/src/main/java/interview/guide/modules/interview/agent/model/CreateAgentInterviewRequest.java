package interview.guide.modules.interview.agent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建 Agent 面试请求。
 */
public record CreateAgentInterviewRequest(
    @NotBlank(message = "JD 不能为空")
    @Size(max = 20000, message = "JD 最多 20000 字符")
    String jd,

    @NotBlank(message = "简历不能为空")
    @Size(max = 100000, message = "简历最多 100000 字符")
    String resume
) {}
