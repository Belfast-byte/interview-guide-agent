package interview.guide.modules.interview.agent.adaptive.rubric;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RubricGenerationRepository extends JpaRepository<RubricGenerationJob, String> {
  @Query("select j.id from RubricGenerationJob j where j.status in ('PENDING','PROCESSING') and j.availableAt <= :now order by j.createdAt")
  List<String> pending(@Param("now") LocalDateTime now, org.springframework.data.domain.Pageable page);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from RubricGenerationJob j where j.id = :id")
  Optional<RubricGenerationJob> locked(@Param("id") String id);
}
