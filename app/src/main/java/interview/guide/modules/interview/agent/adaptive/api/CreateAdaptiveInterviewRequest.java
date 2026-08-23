package interview.guide.modules.interview.agent.adaptive.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建自适应面试请求。
 */
public record CreateAdaptiveInterviewRequest(
    @NotBlank(message = "JD 不能为空") String jd,
    @NotBlank(message = "简历不能为空") String resume,
    @Size(max = 64, message = "LLM Provider 标识不能超过 64 个字符") String providerId
) {}
