package interview.guide.modules.interview.agent.adaptive.core.context;

import java.util.Objects;

/**
 * 长期记忆主题的稳定身份；focusId 只有和 skillId 组合后才唯一。
 */
public record TopicKey(String skillId, String focusId) {

  public TopicKey {
    skillId = requireText(skillId, "skillId");
    focusId = requireText(focusId, "focusId");
  }

  private static String requireText(String value, String field) {
    Objects.requireNonNull(value, field + " 不能为空");
    String normalized = value.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(field + " 不能为空");
    }
    return normalized;
  }
}
