package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import java.time.LocalDateTime;

public interface AdaptiveInterviewSummaryProjection {

  String getSessionId();

  AdaptiveSessionStatus getStatus();

  int getCurrentTurn();

  int getMaxTurns();

  String getJd();

  LocalDateTime getCreatedAt();

  LocalDateTime getCompletedAt();
}
