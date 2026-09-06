package interview.guide.modules.interview.agent.adaptive.rubric;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class RubricGenerationConfiguration {
  // 自定义调度器会使 Boot 默认调度器退让，显式保留其他 @Scheduled 的执行池。
  @Bean(name="taskScheduler")
  @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(name="taskScheduler")
  public ThreadPoolTaskScheduler defaultScheduler() {
    var scheduler=new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("scheduling-");
    return scheduler;
  }
  @Bean(name="rubricGenerationScheduler")
  public ThreadPoolTaskScheduler scheduler() {
    var scheduler=new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("rubric-generation-");
    scheduler.setWaitForTasksToCompleteOnShutdown(false);
    return scheduler;
  }
}
