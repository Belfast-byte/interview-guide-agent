package interview.guide.modules.interview.agent.adaptive.codeanalysis.job;

/**
 * 代码分析任务状态枚举。
 */
public enum AnalysisJobStatus {
  PENDING,
  RUNNING,
  COMPLETED,
  FAILED,
  TIMED_OUT;

  /**
   * 是否为终态，终态任务不允许被迟到回调翻转。
   */
  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED || this == TIMED_OUT;
  }

  /**
   * 是否为可重新投递的失败终态。
   */
  public boolean isResubmittable() {
    return this == FAILED || this == TIMED_OUT;
  }
}
