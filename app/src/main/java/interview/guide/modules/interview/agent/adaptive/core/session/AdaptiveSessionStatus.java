package interview.guide.modules.interview.agent.adaptive.core.session;

/**
 * 自适应面试会话状态枚举。FAILED 是创建链路的终态：规划或首题生成失败后会话停在 FAILED，不可作答。
 */
public enum AdaptiveSessionStatus {
  CREATED,
  IN_PROGRESS,
  COMPLETED,
  FAILED
}
