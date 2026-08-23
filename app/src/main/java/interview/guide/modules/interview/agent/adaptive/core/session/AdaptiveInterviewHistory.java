package interview.guide.modules.interview.agent.adaptive.core.session;

import java.util.List;

/**
 * 自适应面试历史快照，包含会话、轮次列表和状态迁移信息。
 */
public record AdaptiveInterviewHistory(
    AdaptiveInterviewSession session,
    String candidateId,
    String jd,
    String resume,
    String llmProvider,
    String llmProviderNameSnapshot,
    String llmModelSnapshot,
    List<AdaptiveInterviewTurn> turns,
    String failureReason
) {

  public AdaptiveInterviewHistory {
    turns = List.copyOf(turns);
  }

  public AdaptiveInterviewHistory(
      AdaptiveInterviewSession session,
      String candidateId,
      String jd,
      String resume,
      String llmProvider,
      List<AdaptiveInterviewTurn> turns
  ) {
    this(session, candidateId, jd, resume, llmProvider, null, null, turns, null);
  }

  public AdaptiveInterviewHistory(
      AdaptiveInterviewSession session,
      String candidateId,
      String jd,
      String resume,
      String llmProvider,
      List<AdaptiveInterviewTurn> turns,
      String failureReason
  ) {
    this(
        session,
        candidateId,
        jd,
        resume,
        llmProvider,
        null,
        null,
        turns,
        failureReason
    );
  }
}
