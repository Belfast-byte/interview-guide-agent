package interview.guide.modules.interview.agent.adaptive.persistence.intent;

import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdaptiveActionIntentRepository
    extends JpaRepository<AdaptiveActionIntentEntity, String> {

  boolean existsByActiveSessionId(String sessionId);

  List<AdaptiveActionIntentEntity> findByStatusInOrStatusAndExecutionStartedAtBeforeOrderByCreatedAt(
      Collection<ActionIntentStatus> statuses,
      ActionIntentStatus executing,
      LocalDateTime cutoff
  );
}
