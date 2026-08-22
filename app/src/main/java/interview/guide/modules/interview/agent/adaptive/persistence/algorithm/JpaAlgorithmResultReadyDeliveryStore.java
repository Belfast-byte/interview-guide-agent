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
 * 基于 JPA 的算法结果就绪投递存储实现。
 *
 * <p>找出已进入终态（DONE / TIMEOUT_QUEUED）、已过稳定期、所属会话仍在进行中、且
 * agent_tool_result_events 尚未记录对应 (toolName, resultId) 唤醒事件的判题执行，即“结果已落库
 * 但编排器唤醒未送达”的执行，供调度器重新触发唤醒。
 */
@Service
public class JpaAlgorithmResultReadyDeliveryStore implements AlgorithmResultReadyDeliveryStore {

  private final EntityManager entityManager;

  JpaAlgorithmResultReadyDeliveryStore(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  @Transactional(readOnly = true)
  public List<String> findUndeliveredBefore(String toolName, LocalDateTime settledBefore) {
    return entityManager.createQuery("""
        select execution.id
        from SandboxExecutionEntity execution
        where execution.status in :statuses
          and execution.finishedAt < :settledBefore
          and execution.supersededBy is null
          and exists (select session from AdaptiveAgentSessionEntity session
                      where session.id = execution.sessionId
                        and session.status = :inProgress)
          and not exists (
            select event from AdaptiveAgentToolResultEventEntity event
            where event.toolName = :toolName and event.resultId = execution.id
          )
        """, String.class)
        .setParameter("statuses", List.of(
            SandboxExecutionStatus.DONE,
            SandboxExecutionStatus.TIMEOUT_QUEUED
        ))
        .setParameter("settledBefore", settledBefore)
        .setParameter("inProgress", AdaptiveSessionStatus.IN_PROGRESS)
        .setParameter("toolName", toolName)
        .getResultList();
  }
}
