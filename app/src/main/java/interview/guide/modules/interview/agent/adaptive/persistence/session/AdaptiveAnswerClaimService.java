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
  public ClaimResult claim(String sessionId, MemoryOwner owner, CandidateAnswer answer, String executionToken, java.time.Duration lease) {
    AdaptiveAgentSessionEntity session = sessions.findLockedById(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND, "Agent 面试会话不存在"));
    requireOwner(session, owner);
    AdaptiveAgentTurnEntity turn = turns.findLockedBySessionIdAndTurnIndex(
            sessionId, answer.turnIndex())
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "面试轮次不存在"));
    if (turn.answer() != null && !turn.candidateAnswer().equals(answer)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "当前轮次已提交不同回答");
    }
    if (assessments.findBySessionIdAndTurnIndex(sessionId, answer.turnIndex()).isPresent()) {
      return ClaimResult.COMMITTED;
    }
    session.toDomain().assertCanAnswer(answer);
    if (turn.processing()) {
      return ClaimResult.PENDING;
    }
    if (turn.answer() == null) turn.recordAnswer(answer);
    turn.claimExecution(executionToken, lease);
    return ClaimResult.NEW;
  }

  @Transactional
  public void fail(String sessionId, MemoryOwner owner, int turnIndex, String token, String message) {
    var session = sessions.findLockedById(sessionId).orElseThrow();
    requireOwner(session, owner);
    turns.findLockedBySessionIdAndTurnIndex(sessionId, turnIndex)
        .ifPresent(turn -> turn.failExecution(token, message));
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
