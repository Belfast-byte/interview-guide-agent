package interview.guide.modules.interview.agent.adaptive.core.session;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;

/**
 * 自适应面试会话领域对象，封装状态、候选人信息、当前轮次与回答校验。
 */
public record AdaptiveInterviewSession(
    String id,
    String runtimeVersion,
    AdaptiveSessionStatus status,
    int currentTurn,
    int maxTurns,
    InterviewSessionSettings settings
) {

  public static final String RUNTIME_VERSION = "adaptive-agent-v1";

  /**
   * 面试轮次上限，规划裁决与会话创建共用。
   */
  public static final int MAX_TURNS = 12;

  public static final int FINISH_MIN_TURN_DIVISOR = 2;

  public static AdaptiveInterviewSession create(
      String id,
      int maxTurns,
      InterviewSessionSettings settings
  ) {
    if (maxTurns < 1 || maxTurns > MAX_TURNS) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "面试轮次上限必须在 1 到 12 之间");
    }
    return new AdaptiveInterviewSession(
        id,
        RUNTIME_VERSION,
        AdaptiveSessionStatus.CREATED,
        0,
        maxTurns,
        settings
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
        maxTurns,
        settings
    );
  }

  public SessionTransition apply(CandidateAnswer answer, RespondAction proposedAction) {
    assertCanAnswer(answer);

    if (proposedAction.type() == AgentResponseType.FINISH
        && !canFinishEarly(maxTurns, currentTurn)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "面试轮次尚未达到可提前结束的门槛");
    }
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
        new AdaptiveInterviewSession(
            id,
            runtimeVersion,
            nextStatus,
            nextTurn,
            maxTurns,
            settings
        ),
        appliedAction
    );
  }

  /**
   * 模型提案 FINISH 的轮次门槛：已回答轮次达到计划轮次的至少一半才接受提前结束。
   *
   * @param maxTurns 计划轮次上限
   * @param answeredTurns 已回答轮次
   * @return 是否允许提前结束
   */
  public static boolean canFinishEarly(int maxTurns, int answeredTurns) {
    return answeredTurns >= (maxTurns + FINISH_MIN_TURN_DIVISOR - 1) / FINISH_MIN_TURN_DIVISOR;
  }

  public void assertCanAnswer(CandidateAnswer answer) {
    if (status == AdaptiveSessionStatus.COMPLETED) {
      throw new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED, "Agent 面试已经结束");
    }
    if (status == AdaptiveSessionStatus.FAILED) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 面试创建失败，无法继续作答");
    }
    if (status != AdaptiveSessionStatus.IN_PROGRESS) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 面试尚未开始");
    }
    if (answer.turnIndex() != currentTurn) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "提交的回答轮次与当前轮次不一致");
    }
  }
}
