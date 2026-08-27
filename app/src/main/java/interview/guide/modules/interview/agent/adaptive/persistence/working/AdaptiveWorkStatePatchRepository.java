package interview.guide.modules.interview.agent.adaptive.persistence.working;

import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatchSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdaptiveWorkStatePatchRepository
    extends JpaRepository<AdaptiveWorkStatePatchEntity, String> {

  boolean existsBySessionIdAndSourceTypeAndSourceId(
      String sessionId,
      WorkStatePatchSource sourceType,
      String sourceId
  );
}
