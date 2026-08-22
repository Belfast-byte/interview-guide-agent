package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxRunMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

/**
 * 候选人代码提交请求。
 */
public record CandidateCodeSubmissionRequest(
    @Size(max = 64, message = "算法题标识不能超过 64 个字符")
    String problemId,
    @Size(max = 64, message = "场景标识不能超过 64 个字符")
    String scenarioId,
    @NotNull(message = "编程语言不能为空") SandboxLanguage language,
    @NotNull(message = "运行模式不能为空") SandboxRunMode runMode
) {

  @AssertTrue(message = "算法题标识和场景标识必须且只能提供一个")
  public boolean hasSingleTarget() {
    return (problemId == null) != (scenarioId == null);
  }

  @AssertTrue(message = "PATCH 场景只支持完整判题")
  public boolean patchUsesFullRunMode() {
    return scenarioId == null || runMode == SandboxRunMode.FULL;
  }
}
