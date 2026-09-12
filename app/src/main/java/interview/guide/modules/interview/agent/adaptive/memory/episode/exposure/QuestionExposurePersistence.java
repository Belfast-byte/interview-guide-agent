package interview.guide.modules.interview.agent.adaptive.memory.episode.exposure;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.memory.episode.exposure.QuestionExposure.QuestionPublication;
import interview.guide.modules.interview.agent.adaptive.memory.episode.exposure.QuestionExposureEntity.QuestionExposureCreation;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnEntity;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuestionExposurePersistence {

  private static final String DOCUMENT_PREFIX = "question-exposure:";

  private final QuestionExposureRepository repository;

  public QuestionExposure save(
      AdaptiveAgentSessionEntity session,
      AdaptiveAgentTurnEntity turn,
      QuestionPublication publication
  ) {
    // 历史表仍要求非空文档身份；保留兼容值，曝光不再触发向量索引。
    String documentId = DOCUMENT_PREFIX + UUID.randomUUID();
    QuestionExposureCreation creation = new QuestionExposureCreation(
        new MemoryOwner(session.tenantId(), session.candidateId()),
        session.id(),
        turn.id(),
        publication,
        documentId
    );
    return repository.saveAndFlush(new QuestionExposureEntity(creation)).toDomain();
  }
}
