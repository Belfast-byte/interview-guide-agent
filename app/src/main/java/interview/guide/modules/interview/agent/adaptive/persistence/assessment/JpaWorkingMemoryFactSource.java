package interview.guide.modules.interview.agent.adaptive.persistence.assessment;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.working.ProbeGapCandidate;
import interview.guide.modules.interview.agent.adaptive.memory.working.WorkingMemoryFactSource;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 从 PostgreSQL 权威事实恢复当前会话的 ProbeGap 候选。 */
@Service
@RequiredArgsConstructor
public class JpaWorkingMemoryFactSource implements WorkingMemoryFactSource {

  private final AdaptiveAgentSessionRepository sessionRepository;
  private final AdaptiveAgentPlanRepository planRepository;
  private final AssessmentProbeGapRepository gapRepository;

  @Override
  @Transactional(readOnly = true)
  public List<ProbeGapCandidate> findProbeGaps(
      MemoryOwner owner,
      String sessionId
  ) {
    requireOwnedSession(owner, sessionId);
    Map<Integer, TopicKey> topics = planRepository
        .findBySessionIdOrderByDimensionOrder(sessionId)
        .stream()
        .collect(Collectors.toUnmodifiableMap(
            AdaptiveAgentPlanEntity::dimensionOrder,
            AdaptiveAgentPlanEntity::topic
        ));
    return gapRepository.findSessionGaps(sessionId).stream()
        .map(gap -> toCandidate(gap, topics))
        .toList();
  }

  private void requireOwnedSession(MemoryOwner owner, String sessionId) {
    boolean exists = owner.tenantId() == null
        ? sessionRepository.findByIdAndCandidateIdAndTenantIdIsNull(
            sessionId,
            owner.candidateId()
        ).isPresent()
        : sessionRepository.findByIdAndCandidateIdAndTenantId(
            sessionId,
            owner.candidateId(),
            owner.tenantId()
        ).isPresent();
    if (!exists) {
      throw new BusinessException(
          ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
          "Agent 面试会话不存在"
      );
    }
  }

  private ProbeGapCandidate toCandidate(
      AssessmentProbeGapEntity gap,
      Map<Integer, TopicKey> topics
  ) {
    TopicKey topic = topics.get(gap.assessmentDimensionOrder());
    if (topic == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "ProbeGap 缺少计划主题");
    }
    return new ProbeGapCandidate(
        gap.id(),
        gap.assessmentId(),
        gap.assessmentTurnIndex(),
        topic,
        gap.gapOrder(),
        gap.toDomain()
    );
  }
}
