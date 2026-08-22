package interview.guide.modules.interview.agent.adaptive.application;

/**
 * 自适应面试创建任务执行器抽象：生产实现为后台线程池，测试可注入同步执行。
 */
interface AdaptiveInterviewCreationTaskRunner {

  /**
   * 提交创建链路后台任务；队列打满时抛出 {@link java.util.concurrent.RejectedExecutionException}。
   */
  void submit(Runnable task);
}
