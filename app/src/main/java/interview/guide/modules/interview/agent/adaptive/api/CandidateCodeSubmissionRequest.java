package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxRunMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CandidateCodeSubmissionRequest(
    @NotBlank(message = "算法题标识不能为空")
    @Size(max = 64, message = "算法题标识不能超过 64 个字符")
    String problemId,
    @NotNull(message = "编程语言不能为空") SandboxLanguage language,
    @NotNull(message = "运行模式不能为空") SandboxRunMode runMode
) {}
