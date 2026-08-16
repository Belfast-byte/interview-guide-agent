package interview.guide.modules.interview.agent.adaptive.persistence.algorithm;

import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmAssessmentMetricsSource;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionStatus;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxVerdict;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 JPA 的算法评估指标来源实现。
 */
@Service
@RequiredArgsConstructor
class JpaAlgorithmAssessmentMetricsSource implements AlgorithmAssessmentMetricsSource {

  private final EntityManager entityManager;

  @Override
  @Transactional(readOnly = true)
  public boolean hasActiveJudging(String sessionId) {
    return entityManager.createQuery("""
        select count(execution)
        from SandboxExecutionEntity execution
        where execution.sessionId = :sessionId
          and execution.status in :statuses
        """, Long.class)
        .setParameter("sessionId", sessionId)
        .setParameter("statuses", List.of(
            SandboxExecutionStatus.PENDING,
            SandboxExecutionStatus.RUNNING
        ))
        .getSingleResult() > 0;
  }

  @Override
  @Transactional(readOnly = true)
  public long countAssessmentsWithValidResults() {
    return count("""
        SELECT COUNT(DISTINCT assessment.id)
        FROM agent_assessments assessment
        JOIN agent_turns turn
          ON turn.session_id = assessment.session_id
          AND turn.turn_index = assessment.turn_index
        JOIN sandbox_executions execution ON execution.turn_id = turn.id
        WHERE execution.status = 'DONE'
          AND execution.verdict IS NOT NULL
          AND execution.verdict <> 'IE'
          AND execution.superseded_by IS NULL
        """);
  }

  @Override
  @Transactional(readOnly = true)
  public long countAssessmentsWithSandboxEvidence() {
    return count("""
        SELECT COUNT(DISTINCT assessment.id)
        FROM agent_assessments assessment
        JOIN agent_evidences evidence ON evidence.assessment_id = assessment.id
        WHERE evidence.sandbox_execution_id IS NOT NULL
        """);
  }

  @Override
  @Transactional(readOnly = true)
  public long countReviewRequiredAssessments() {
    return count("""
        SELECT COUNT(DISTINCT assessment.id)
        FROM agent_assessments assessment
        JOIN agent_evidences evidence ON evidence.assessment_id = assessment.id
        JOIN sandbox_executions execution
          ON execution.id = evidence.sandbox_execution_id
        WHERE execution.verdict = 'WA'
          AND assessment.depth_level IN ('L3', 'L4')
        """);
  }

  private long count(String sql) {
    return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
  }
}
