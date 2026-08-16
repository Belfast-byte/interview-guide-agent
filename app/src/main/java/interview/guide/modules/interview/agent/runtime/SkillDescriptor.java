package interview.guide.modules.interview.agent.runtime;

/**
 * 技能描述信息，用于向模型展示可加载的面试技能。
 */
public record SkillDescriptor(String id, String name, String description) {}
