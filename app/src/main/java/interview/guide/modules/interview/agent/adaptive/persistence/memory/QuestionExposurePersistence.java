package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionExposure;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionExposurePublished;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionPublication;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnEntity;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuestionExposurePersistence {

  private static final String DOCUMENT_PREFIX = "question-exposure:";

  private final QuestionExposureRepository repository;
  private final ApplicationEventPublisher eventPublisher;

  public QuestionExposure save(
      AdaptiveAgentSessionEntity session,
      AdaptiveAgentTurnEntity turn,
      QuestionPublication publication
  ) {
    String documentId = DOCUMENT_PREFIX + UUID.randomUUID();
    QuestionExposureCreation creation = new QuestionExposureCreation(
        new MemoryOwner(session.tenantId(), session.candidateId()),
        session.id(),
        turn.id(),
        publication,
        documentId
    );
    QuestionExposure exposure = repository.saveAndFlush(
        new QuestionExposureEntity(creation)).toDomain();
    eventPublisher.publishEvent(new QuestionExposurePublished(exposure));
    return exposure;
  }
}
