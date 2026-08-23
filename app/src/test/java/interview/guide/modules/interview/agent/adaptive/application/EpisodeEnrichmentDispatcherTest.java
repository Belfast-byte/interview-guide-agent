package interview.guide.modules.interview.agent.adaptive.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentRequested;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentService;
import java.lang.reflect.Method;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class EpisodeEnrichmentDispatcherTest {

  private final AdaptiveInterviewAnswerExecutor executor = mock(
      AdaptiveInterviewAnswerExecutor.class
  );
  private final EpisodeEnrichmentService service = mock(EpisodeEnrichmentService.class);
  private final EpisodeEnrichmentDispatcher dispatcher = new EpisodeEnrichmentDispatcher(
      executor,
      service
  );

  @Test
  @DisplayName("Episode 只在提交后事件中唤醒异步 worker")
  void shouldDispatchAfterCommit() throws NoSuchMethodException {
    doAnswer(invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return null;
    }).when(executor).execute(any(Runnable.class));

    dispatcher.onRequested(new EpisodeEnrichmentRequested(12, "provider-a"));

    verify(service).enrich(12, "provider-a");
    Method method = EpisodeEnrichmentDispatcher.class.getMethod(
        "onRequested",
        EpisodeEnrichmentRequested.class
    );
    assertThat(method.getAnnotation(TransactionalEventListener.class).phase())
        .isEqualTo(TransactionPhase.AFTER_COMMIT);
  }

  @Test
  @DisplayName("后台队列拒绝唤醒时错误明确暴露")
  void shouldExposeRejectedWakeUp() {
    RejectedExecutionException failure = new RejectedExecutionException("queue full");
    doThrow(failure).when(executor).execute(any(Runnable.class));

    assertThatThrownBy(() -> dispatcher.onRequested(
        new EpisodeEnrichmentRequested(12, "provider-a")
    )).isSameAs(failure);
  }
}
