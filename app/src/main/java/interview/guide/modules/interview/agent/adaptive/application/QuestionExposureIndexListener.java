package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionExposurePublished;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.QuestionExposureVectorStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** turn 与 exposure 提交后同步写入 vector_store；失败直接暴露给调用链。 */
@Component
@RequiredArgsConstructor
public class QuestionExposureIndexListener {

  private final QuestionExposureVectorStore vectorStore;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPublished(QuestionExposurePublished event) {
    vectorStore.index(event.exposure());
  }
}
