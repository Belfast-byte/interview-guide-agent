package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import jakarta.validation.constraints.NotBlank;

/** 练习模式中由用户明确选择的一个主题。 */
public record PracticeTopicRequest(
    @NotBlank(message = "Skill 标识不能为空") String skillId,
    @NotBlank(message = "考察重点标识不能为空") String focusId
) {

  TopicKey toTopicKey() {
    return new TopicKey(skillId, focusId);
  }
}
