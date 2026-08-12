package interview.guide.modules.interview.agent.adaptive.core;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;

public record AdaptiveInterviewSession(
    String id,
    String runtimeVersion,
    AdaptiveSessionStatus status,
    int currentTurn,
    int maxTurns
) {

  public static final String RUNTIME_VERSION = "adaptive-agent-v1";

  public static AdaptiveInterviewSession create(String id, int maxTurns) {
    if (maxTurns < 1 || maxTurns > 12) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "面试轮次上限必须在 1 到 12 之间");
    }
    return new AdaptiveInterviewSession(
        id,
        RUNTIME_VERSION,
        AdaptiveSessionStatus.CREATED,
        0,
        maxTurns
    );
  }

  public AdaptiveInterviewSession start() {
    if (status != AdaptiveSessionStatus.CREATED) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "只有新建会话可以开始面试");
    }
    return new AdaptiveInterviewSession(
        id,
        runtimeVersion,
        AdaptiveSessionStatus.IN_PROGRESS,
        1,
        maxTurns
    );
  }

  public SessionTransition apply(CandidateAnswer answer, RespondAction proposedAction) {
    assertCanAnswer(answer);

    RespondAction appliedAction = proposedAction;
    if (proposedAction.type() == AgentResponseType.ASK && currentTurn == maxTurns) {
      appliedAction = RespondAction.finish("面试已达到轮次上限。", "轮次预算已用尽");
    }

    AdaptiveSessionStatus nextStatus = appliedAction.type() == AgentResponseType.FINISH
        ? AdaptiveSessionStatus.COMPLETED
        : AdaptiveSessionStatus.IN_PROGRESS;
    int nextTurn = appliedAction.type() == AgentResponseType.ASK
        ? currentTurn + 1
        : currentTurn;

    return new SessionTransition(
        new AdaptiveInterviewSession(id, runtimeVersion, nextStatus, nextTurn, maxTurns),
        appliedAction
    );
  }

  public void assertCanAnswer(CandidateAnswer answer) {
    if (status == AdaptiveSessionStatus.COMPLETED) {
      throw new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED, "Agent 面试已经结束");
    }
    if (status != AdaptiveSessionStatus.IN_PROGRESS) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 面试尚未开始");
    }
    if (answer.turnIndex() != currentTurn) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "提交的回答轮次与当前轮次不一致");
    }
  }
}
