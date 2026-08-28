package interview.guide.modules.interview.agent.adaptive.persistence.assessment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Assessment 追问缺口仓储。
 */
public interface AssessmentProbeGapRepository
    extends JpaRepository<AssessmentProbeGapEntity, Long> {

  List<AssessmentProbeGapEntity> findByAssessmentIdOrderByGapOrderAscIdAsc(
      Long assessmentId
  );

  @Query("""
      SELECT gap
      FROM AssessmentProbeGapEntity gap
      JOIN FETCH gap.assessment assessment
      WHERE assessment.sessionId = :sessionId
      ORDER BY gap.gapOrder ASC, gap.id ASC
      """)
  List<AssessmentProbeGapEntity> findSessionGaps(
      @Param("sessionId") String sessionId
  );

  @Query("""
      SELECT gap
      FROM AssessmentProbeGapEntity gap
      JOIN FETCH gap.assessment assessment
      WHERE assessment.id IN :assessmentIds
      ORDER BY assessment.id, gap.gapOrder, gap.id
      """)
  List<AssessmentProbeGapEntity> findByAssessmentIds(
      @Param("assessmentIds") List<Long> assessmentIds
  );
}
