package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;

/** 服务端身份边界；模型不能指定 owner 或跨会话读取。 */
final class SessionReadBoundary {
  private SessionReadBoundary() {}

  static AdaptiveAgentSessionEntity session(AgentContext context, AdaptiveAgentSessionRepository sessions) {
    var identity = context.session().identity();
    var owner = identity.owner();
    var session = owner.tenantId() == null
        ? sessions.findByIdAndCandidateIdAndTenantIdIsNull(identity.sessionId(), owner.candidateId())
        : sessions.findByIdAndCandidateIdAndTenantId(identity.sessionId(), owner.candidateId(), owner.tenantId());
    return session.orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND, "面试会话不存在"));
  }

  static void requireTurnIndex(int index) {
    if (index <= 0) throw new ReadToolValidationException("turnIndex", "必须为正整数");
  }
}
