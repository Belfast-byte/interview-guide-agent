package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import interview.guide.modules.interview.skill.InterviewSkillService;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.planning.PlannerContext;
import java.util.List;
import interview.guide.modules.interview.agent.adaptive.runtime.AdaptiveAgentRuntimeConfiguration.QueryTools;
import org.springframework.stereotype.Component;

/**
 * 上下文装配器，为规划器、面试官、评估器等角色组装所需上下文。
 */
@Component
public class ContextAssembler {
  private final InterviewSkillService skillService;
  private final List<String> allowedReadTools;

  public ContextAssembler(InterviewSkillService skillService,
      QueryTools tools) {
    this.skillService = skillService;
    this.allowedReadTools = tools.callbacks().stream()
        .map(tool -> tool.getToolDefinition().name()).toList();
  }

  /** 创建唯一的中性 AgentContext，并严格加载 Plan 固定 Skill。 */
  public AgentContext agent(AgentContextInput input) {
    List<AgentContext.SkillGuidance> skillGuidance = input.dimensions().stream()
        .map(PlannedDimension::suggestedSkill)
        .distinct()
        .map(skillId -> new AgentContext.SkillGuidance(
            skillId, skillService.decisionInstructions(skillId)))
        .toList();
    return new AgentContext(
        new AgentContext.SessionWindow(
            new AgentContext.SessionIdentity(
                input.sessionId(), input.llmProvider(), input.owner()),
            input.mode(),
            input.maxTurns()
        ),
        new AgentContext.Facts(
            input.coverage(), input.recentTurns(), skillGuidance, allowedReadTools),
        input.workingMemory()
    );
  }


  /** 本场材料保留原文，输入超限交给已有 token budget 显式报告。 */
  public PlannerContext planner(PlannerContext input) {
    return input;
  }

  public record AgentContextInput(
      MemoryOwner owner,
      String sessionId,
      String llmProvider,
      SessionMode mode,
      int maxTurns,
      List<PlannedDimension> dimensions,
      CoverageView coverage,
      List<AdaptiveInterviewTurn> recentTurns,
      WorkingMemory workingMemory
  ) {

    public AgentContextInput {
      dimensions = List.copyOf(dimensions);
      recentTurns = List.copyOf(recentTurns);
    }
  }

}
