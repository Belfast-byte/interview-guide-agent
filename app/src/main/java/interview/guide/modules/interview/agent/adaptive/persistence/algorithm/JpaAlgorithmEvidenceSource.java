package interview.guide.modules.interview.agent.adaptive.persistence.algorithm;

import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmEvidence;
import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmEvidenceSource;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionStatus;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxVerdict;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
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
  public Map<String, String> findCandidateEvidenceIds(
      String sessionId,
      int turnIndex,
      Set<String> resultIds
  ) {
    if (resultIds.isEmpty()) {
      return Map.of();
    }
    return entityManager.createQuery("""
        select execution.id
        from SandboxExecutionEntity execution, AdaptiveAgentTurnEntity turn
        where execution.turnId = turn.id
          and execution.sessionId = :sessionId
          and turn.turnIndex = :turnIndex
          and execution.id in :resultIds
          and execution.status = :status
          and execution.verdict is not null
          and execution.verdict <> :internalError
          and execution.supersededBy is null
        """, String.class)
        .setParameter("sessionId", sessionId)
        .setParameter("turnIndex", turnIndex)
        .setParameter("resultIds", resultIds)
        .setParameter("status", SandboxExecutionStatus.DONE)
        .setParameter("internalError", SandboxVerdict.IE)
        .getResultStream()
        .collect(Collectors.toMap(id -> id, id -> id));
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, String> findCandidateEvidenceIds(
      String sessionId,
      int turnIndex
  ) {
    return candidateEvidenceQuery(sessionId, turnIndex)
        .getResultStream()
        .collect(Collectors.toMap(id -> id, id -> id));
  }

  @Override
  @Transactional(readOnly = true)
  public Set<Integer> findCandidateTurnIndexes(String sessionId) {
    return entityManager.createQuery("""
        select distinct turn.turnIndex
        from SandboxExecutionEntity execution, AdaptiveAgentTurnEntity turn
        where execution.turnId = turn.id
          and execution.sessionId = :sessionId
          and execution.status = :status
          and execution.verdict is not null
          and execution.verdict <> :internalError
          and execution.supersededBy is null
        """, Integer.class)
        .setParameter("sessionId", sessionId)
        .setParameter("status", SandboxExecutionStatus.DONE)
        .setParameter("internalError", SandboxVerdict.IE)
        .getResultStream()
        .collect(Collectors.toSet());
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
    String summary = "verdict=%s, passed=%s/%s, timeMs=%s, memoryKb=%s, firstFailedCase=%s"
        .formatted(row[1], row[2], row[3], row[4], row[5], row[6]);
    return new AlgorithmEvidence((String) row[0], summary);
  }

  private TypedQuery<String> candidateEvidenceQuery(
      String sessionId,
      int turnIndex
  ) {
    return entityManager.createQuery("""
        select execution.id
        from SandboxExecutionEntity execution, AdaptiveAgentTurnEntity turn
        where execution.turnId = turn.id
          and execution.sessionId = :sessionId
          and turn.turnIndex = :turnIndex
          and execution.status = :status
          and execution.verdict is not null
          and execution.verdict <> :internalError
          and execution.supersededBy is null
        """, String.class)
        .setParameter("sessionId", sessionId)
        .setParameter("turnIndex", turnIndex)
        .setParameter("status", SandboxExecutionStatus.DONE)
        .setParameter("internalError", SandboxVerdict.IE);
  }
}
