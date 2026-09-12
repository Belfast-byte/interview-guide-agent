package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import java.math.BigDecimal;
import java.util.Set;

/** 材料和按轮读取共享同一个服务端身份边界，不接受模型指定 owner。 */
final class SessionReadBoundary {
  private SessionReadBoundary() {}

  static AdaptiveAgentSessionEntity session(ReadToolRequest request,
      AdaptiveAgentSessionRepository sessions) {
    var identity = request.context().session().identity();
    var owner = identity.owner();
    var session = owner.tenantId() == null
        ? sessions.findByIdAndCandidateIdAndTenantIdIsNull(identity.sessionId(), owner.candidateId())
        : sessions.findByIdAndCandidateIdAndTenantId(identity.sessionId(), owner.candidateId(), owner.tenantId());
    return session.orElseThrow(() -> new BusinessException(
        ErrorCode.INTERVIEW_SESSION_NOT_FOUND, "面试会话不存在"));
  }

  static int turnIndex(ReadToolRequest request) {
    if (!request.arguments().keySet().equals(Set.of("turnIndex"))) {
      throw new ReadToolValidationException("arguments", "只接受 turnIndex");
    }
    Object value = request.arguments().get("turnIndex");
    if (!(value instanceof Number number)) {
      throw new ReadToolValidationException("arguments.turnIndex", "必须为正整数");
    }
    try {
      int index = new BigDecimal(number.toString()).intValueExact();
      if (index > 0) return index;
    } catch (ArithmeticException | NumberFormatException exception) {
      throw new ReadToolValidationException("arguments.turnIndex", "必须为正整数");
    }
    throw new ReadToolValidationException("arguments.turnIndex", "必须为正整数");
  }
}
