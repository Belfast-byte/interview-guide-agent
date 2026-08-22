package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 代码分析产物 JSON 反序列化助手。
 */
public final class CodeAnalysisJson {

  private CodeAnalysisJson() {}

  public static <T> T read(ObjectMapper objectMapper, String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JacksonException e) {
      throw new BusinessException(
          ErrorCode.INTERNAL_ERROR,
          "已存储的代码分析产物无效",
          e
      );
    }
  }
}
