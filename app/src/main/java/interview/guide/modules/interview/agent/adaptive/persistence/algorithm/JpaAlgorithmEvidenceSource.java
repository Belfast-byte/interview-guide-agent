package interview.guide.modules.interview.agent.adaptive.persistence.algorithm;

import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmEvidence;
import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmEvidenceSource;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionSummary;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxVerdict;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 JPA 的算法评估证据来源实现。
 */
@Service
public class JpaAlgorithmEvidenceSource implements AlgorithmEvidenceSource {

  private final EntityManager entityManager;

  JpaAlgorithmEvidenceSource(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, AlgorithmEvidence> findEvidence(Set<String> executionIds) {
    if (executionIds.isEmpty()) {
      return Map.of();
    }
    return entityManager.createQuery("""
        select execution.id, execution.verdict, execution.passed,
          execution.total, execution.timeMs, execution.memoryKb,
          execution.firstFailedCase
        from SandboxExecutionEntity execution
        where execution.id in :executionIds
        """, Object[].class)
        .setParameter("executionIds", executionIds)
        .getResultStream()
        .map(this::toEvidence)
        .collect(Collectors.toMap(
            AlgorithmEvidence::executionId,
            evidence -> evidence
        ));
  }

  private AlgorithmEvidence toEvidence(Object[] row) {
    return new AlgorithmEvidence((String) row[0], SandboxExecutionSummary.of(
        (SandboxVerdict) row[1],
        (Integer) row[2],
        (Integer) row[3],
        (Long) row[4],
        (Long) row[5],
        (Integer) row[6]
    ));
  }
}
