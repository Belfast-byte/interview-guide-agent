package interview.guide.modules.interview.agent.adaptive.persistence.algorithm;

import interview.guide.modules.interview.agent.adaptive.algorithm.judge.AlgorithmResultReadyDeliveryStore;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionStatus;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 查找尚未消费为正式证据的终态沙箱执行，供调度器补偿消费。
 */
@Service
public class JpaAlgorithmResultReadyDeliveryStore implements AlgorithmResultReadyDeliveryStore {

  private final EntityManager entityManager;

  JpaAlgorithmResultReadyDeliveryStore(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  @Transactional(readOnly = true)
  public List<String> findUnconsumedBefore(LocalDateTime settledBefore) {
    return entityManager.createQuery("""
        select execution.id
        from SandboxExecutionEntity execution
        where execution.status in :statuses
          and execution.finishedAt < :settledBefore
          and execution.consumedAt is null
          and exists (select session from AdaptiveAgentSessionEntity session
                      where session.id = execution.sessionId
                        and session.status = :inProgress)
        """, String.class)
        .setParameter("statuses", List.of(
            SandboxExecutionStatus.DONE,
            SandboxExecutionStatus.TIMEOUT_QUEUED
        ))
        .setParameter("settledBefore", settledBefore)
        .setParameter("inProgress", AdaptiveSessionStatus.IN_PROGRESS)
        .getResultList();
  }
}
