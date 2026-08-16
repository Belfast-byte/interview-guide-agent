package interview.guide.modules.interview.agent.runtime;

/**
 * 已加载面试技能的值对象，保存技能 ID、名称与描述。
 */
public record LoadedSkill(
    String id,
    String name,
    String description,
    String body,
    String hash
) {}
