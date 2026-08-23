package interview.guide.modules.interview.agent.adaptive.persistence.assessment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Assessment 追问缺口仓储。
 */
public interface AssessmentProbeGapRepository
    extends JpaRepository<AssessmentProbeGapEntity, Long> {

  List<AssessmentProbeGapEntity> findByAssessmentIdOrderByGapOrderAscIdAsc(
      Long assessmentId
  );
}
