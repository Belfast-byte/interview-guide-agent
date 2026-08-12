package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import java.util.Map;

final class ToolArguments {

  private ToolArguments() {}

  static String requiredString(
      Map<String, Object> arguments,
      String name,
      int maxLength
  ) {
    Object value = arguments.get(name);
    if (!(value instanceof String text) || text.isBlank()) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Agent tool argument must be a non-blank string: " + name
      );
    }
    String normalized = text.trim();
    if (normalized.length() > maxLength) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Agent tool argument is too long: " + name
      );
    }
    return normalized;
  }

  static String optionalString(
      Map<String, Object> arguments,
      String name,
      int maxLength
  ) {
    Object value = arguments.get(name);
    if (value == null) {
      return null;
    }
    if (!(value instanceof String text)) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Agent tool argument must be a string: " + name
      );
    }
    if (text.isBlank()) {
      return null;
    }
    String normalized = text.trim();
    if (normalized.length() > maxLength) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Agent tool argument is too long: " + name
      );
    }
    return normalized;
  }
}
