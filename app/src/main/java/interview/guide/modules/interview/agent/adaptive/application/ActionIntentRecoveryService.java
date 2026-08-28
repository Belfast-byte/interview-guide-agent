package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.persistence.intent.ActionIntentPersistenceService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActionIntentRecoveryService {

  private final ActionIntentPersistenceService persistenceService;
  private final PersistentActionCoordinator actionCoordinator;
  private final AdaptiveAgentProperties properties;

  public void recover() {
    LocalDateTime cutoff = LocalDateTime.now()
        .minus(properties.getActionIntentExecutionTimeout());
    persistenceService.recoverable(cutoff)
        .forEach(actionCoordinator::recover);
  }
}
