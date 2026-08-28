package interview.guide.modules.interview.agent.adaptive.persistence.assessment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * AdaptiveAgentEvidenceRepository 数据访问接口，提供相关实体的 Spring Data Repository。
 */
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

  boolean existsByAssessmentIdAndSandboxExecutionId(
      Long assessmentId,
      String sandboxExecutionId
  );

  List<AdaptiveAgentEvidenceEntity> findByAssessmentIdOrderById(Long assessmentId);

  @Query("""
      SELECT evidence
      FROM AdaptiveAgentEvidenceEntity evidence
      JOIN FETCH evidence.assessment assessment
      WHERE assessment.id IN :assessmentIds
      ORDER BY assessment.id, evidence.id
      """)
  List<AdaptiveAgentEvidenceEntity> findByAssessmentIds(
      @Param("assessmentIds") List<Long> assessmentIds
  );

  void deleteByAssessmentId(Long assessmentId);
}
