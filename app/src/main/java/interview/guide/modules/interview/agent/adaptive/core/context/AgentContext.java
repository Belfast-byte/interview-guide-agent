package interview.guide.modules.interview.agent.adaptive.core.context;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import java.util.List;

/** InterviewAgentLoop 的中性输入，不预选 Target、Gap 或下一动作。 */
public record AgentContext(
    SessionWindow session,
    Facts facts,
    WorkingMemory workingMemory
) {

  public record SessionWindow(SessionMode mode, int maxTurns) {}

  public record Facts(
      CoverageView coverage,
      List<AdaptiveInterviewTurn> recentTurns,
      List<String> allowedReadTools
  ) {

    public Facts {
      recentTurns = List.copyOf(recentTurns);
      allowedReadTools = List.copyOf(allowedReadTools);
    }
  }
}
