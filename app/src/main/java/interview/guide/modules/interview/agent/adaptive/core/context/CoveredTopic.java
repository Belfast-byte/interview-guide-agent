package interview.guide.modules.interview.agent.adaptive.core.context;

/**
 * 候选人已覆盖主题值对象，用于长期记忆避免重复出题。
 */
public record CoveredTopic(String skillId, String focusId) {}
