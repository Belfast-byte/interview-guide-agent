package interview.guide.modules.interview.agent.adaptive.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * PracticeRecordRepository 数据访问接口，提供相关实体的 Spring Data Repository。
 */
public interface PracticeRecordRepository
    extends JpaRepository<PracticeRecordEntity, Long> {

  List<PracticeRecordEntity> findBySourceSessionIdOrderByDimensionOrder(
      String sourceSessionId
  );
}
