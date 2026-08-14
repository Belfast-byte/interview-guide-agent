package interview.guide.modules.interview.agent.adaptive.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeRecordRepository
    extends JpaRepository<PracticeRecordEntity, Long> {

  List<PracticeRecordEntity> findBySourceSessionIdOrderByDimensionOrder(
      String sourceSessionId
  );
}
