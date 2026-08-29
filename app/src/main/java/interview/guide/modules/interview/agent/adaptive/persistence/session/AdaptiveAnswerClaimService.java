package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 以当前 Turn 行锁条件写入一次真实回答；相同 payload 重放不重复推进。 */
@Service
public class AdaptiveAnswerClaimService {

  private final AdaptiveAgentSessionRepository sessions;
  private final AdaptiveAgentTurnRepository turns;
  private final AdaptiveAgentAssessmentRepository assessments;

  public AdaptiveAnswerClaimService(
      AdaptiveAgentSessionRepository sessions,
      AdaptiveAgentTurnRepository turns,
      AdaptiveAgentAssessmentRepository assessments
  ) {
    this.sessions = sessions;
    this.turns = turns;
    this.assessments = assessments;
  }

  @Transactional
  public ClaimResult claim(String sessionId, MemoryOwner owner, CandidateAnswer answer) {
    AdaptiveAgentSessionEntity session = sessions.findLockedById(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND, "Agent 面试会话不存在"));
    requireOwner(session, owner);
    AdaptiveAgentTurnEntity turn = turns.findLockedBySessionIdAndTurnIndex(
            sessionId, answer.turnIndex())
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "面试轮次不存在"));
    if (turn.answer() == null) {
      session.toDomain().assertCanAnswer(answer);
      turn.recordAnswer(answer);
      return ClaimResult.NEW;
    }
    if (turn.candidateAnswer().equals(answer)) {
      return assessments.findBySessionIdAndTurnIndex(sessionId, answer.turnIndex()).isPresent()
          ? ClaimResult.COMMITTED
          : ClaimResult.PENDING;
    }
    throw new BusinessException(ErrorCode.BAD_REQUEST, "当前轮次已提交不同回答");
  }

  public enum ClaimResult {
    NEW,
    PENDING,
    COMMITTED
  }

  private void requireOwner(AdaptiveAgentSessionEntity session, MemoryOwner owner) {
    if (!java.util.Objects.equals(session.tenantId(), owner.tenantId())
        || !session.candidateId().equals(owner.candidateId())) {
      throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND, "Agent 面试会话不存在");
    }
  }
}
