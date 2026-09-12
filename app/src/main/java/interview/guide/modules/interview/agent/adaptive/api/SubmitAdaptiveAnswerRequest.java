package interview.guide.modules.interview.agent.adaptive.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxRunMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 提交自适应面试回答请求。
 */
public record SubmitAdaptiveAnswerRequest(
    @Min(value = 1, message = "轮次必须从 1 开始") int turnIndex,
    String answer,
    @Valid CandidateCodeSubmissionRequest codeSubmission,
    @Valid CodeRepairAnswerRequest codeRepair
) {

  public record CodeRepairAnswerRequest(@NotBlank(message = "提交代码不能为空") String code) {
    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
      throw new IllegalArgumentException("codeRepair 不支持字段：" + field);
    }
  }

  @JsonAnySetter
  public void rejectUnknownField(String field, Object value) {
    throw new IllegalArgumentException("答题参数不支持字段：" + field);
  }

  @AssertTrue(message = "代码改错与旧代码执行提交互斥")
  public boolean hasSingleCodeProtocol() {
    return codeRepair == null || codeSubmission == null;
  }

  @AssertTrue(message = "回答或代码不能为空")
  public boolean hasAnswer() {
    return codeRepair != null || (answer != null && !answer.isBlank());
  }

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
}
