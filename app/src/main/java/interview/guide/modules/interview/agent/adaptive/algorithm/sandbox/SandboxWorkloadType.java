package interview.guide.modules.interview.agent.adaptive.algorithm.sandbox;

/**
 * 沙箱负载类型枚举。
 */
public enum SandboxWorkloadType {
  ALGORITHM,
  // 保留历史映射及退役前已创建任务的消费；新的 HTTP 提交只允许算法题。
  PATCH
}
