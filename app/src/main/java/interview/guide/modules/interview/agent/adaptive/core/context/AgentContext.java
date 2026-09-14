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

  public record SessionWindow(
      SessionIdentity identity,
      SessionMode mode,
      int maxTurns
  ) {}

  public record SessionIdentity(
      String sessionId,
      String llmProvider,
      MemoryOwner owner
  ) {}

  public record Facts(
      CoverageView coverage,
      List<AdaptiveInterviewTurn> recentTurns,
      List<SkillGuidance> skillGuidance,
      List<String> allowedReadTools
  ) {

    public Facts {
      recentTurns = List.copyOf(recentTurns);
      skillGuidance = List.copyOf(skillGuidance);
      allowedReadTools = List.copyOf(allowedReadTools);
    }
  }

  public record SkillGuidance(String skillId, String decisionInstructions) {}
}
