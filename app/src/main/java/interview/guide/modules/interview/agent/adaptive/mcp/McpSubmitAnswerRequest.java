package interview.guide.modules.interview.agent.adaptive.mcp;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;

/** MCP 输入边界独立校验；具体题型与当前轮的匹配仍由正式提交事务校验。 */
public record McpSubmitAnswerRequest(int turnIndex, String answer, CodeRepairAnswerRequest codeRepair) {

  private static final int FIRST_TURN_INDEX = 1;

  public record CodeRepairAnswerRequest(String code) {
    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
      throw new IllegalArgumentException("codeRepair 不支持字段：" + field);
    }
  }

  @JsonAnySetter
  public void rejectUnknownField(String field, Object value) {
    throw new IllegalArgumentException("MCP 答题参数不支持字段：" + field);
  }

  void validate() {
    if (turnIndex < FIRST_TURN_INDEX) throw new BusinessException(ErrorCode.BAD_REQUEST, "回答轮次无效");
    if (codeRepair != null) {
      if (codeRepair.code() == null || codeRepair.code().isBlank()) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "提交代码不能为空");
      }
      return;
    }
    if (answer == null || answer.isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "回答或代码不能为空");
    }
  }
}
