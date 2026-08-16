package interview.guide.modules.interview.agent.adaptive.algorithm.api;

import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxRunMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 提交算法代码请求，包含题目、源码、语言与运行模式。
 */
public record SubmitAlgorithmCodeRequest(
    @Positive(message = "轮次必须大于 0") int turnIndex,
    @NotBlank(message = "算法题标识不能为空")
    @Size(max = 64, message = "算法题标识不能超过 64 个字符")
    String problemId,
    @NotNull(message = "编程语言不能为空") SandboxLanguage language,
    @NotBlank(message = "源码不能为空") String source,
    @NotNull(message = "运行模式不能为空") SandboxRunMode runMode
) {}
