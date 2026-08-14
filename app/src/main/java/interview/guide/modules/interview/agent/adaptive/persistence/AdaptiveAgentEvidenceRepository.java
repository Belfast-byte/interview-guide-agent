package interview.guide.modules.interview.agent.adaptive.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdaptiveAgentEvidenceRepository
    extends JpaRepository<AdaptiveAgentEvidenceEntity, Long> {

  @Query("""
      SELECT evidence
      FROM AdaptiveAgentEvidenceEntity evidence
      JOIN FETCH evidence.assessment assessment
      WHERE assessment.sessionId = :sessionId
      ORDER BY assessment.dimensionOrder, assessment.turnIndex, evidence.id
      """)
  List<AdaptiveAgentEvidenceEntity> findReportEvidence(
      @Param("sessionId") String sessionId
  );
}
