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
import org.springframework.stereotype.Component;

/**
 * 上下文装配器，为规划器、面试官、评估器等角色组装所需上下文。
 */
@Component
public class ContextAssembler {
  private static final List<String> ALLOWED_READ_TOOLS = List.of("rubric_search");

  private final InterviewSkillService skillService;

  public ContextAssembler(InterviewSkillService skillService) {
    this.skillService = skillService;
  }

  /** 创建唯一的中性 AgentContext，并严格加载 Plan 固定 Skill。 */
  public AgentContext agent(AgentContextInput input) {
    List<AgentContext.SkillReference> fixedSkills = input.dimensions().stream()
        .map(PlannedDimension::suggestedSkill)
        .distinct()
        .map(skillId -> new AgentContext.SkillReference(
            skillId, skillService.buildEvaluationReferenceSection(skillId)))
        .toList();
    return new AgentContext(
        new AgentContext.SessionWindow(
            new AgentContext.SessionIdentity(
                input.sessionId(), input.llmProvider(), input.owner()),
            input.mode(),
            input.maxTurns()
        ),
        new AgentContext.Facts(
            input.coverage(), input.recentTurns(), fixedSkills, ALLOWED_READ_TOOLS),
        input.workingMemory()
    );
  }


  /**
   * JD 与简历注入上下文的最大字符数，超出部分截断并标注，控制每次调用的输入预算。
   */
  private static final int MAX_DOCUMENT_CHARS = 6_000;
  private static final String TRUNCATION_MARKER = "……[原文共 %d 字符，超出部分已截断]";

  /** 组装规划 Agent 所需的上下文，并裁剪过长的本次文档。 */
  public PlannerContext planner(PlannerContext input) {
    return new PlannerContext(
        truncate(input.jd()),
        truncate(input.resume()),
        input.mode(),
        input.candidateLevel(),
        input.practiceScope(),
        input.skillCatalog()
    );
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

  private String truncate(String document) {
    if (document == null || document.length() <= MAX_DOCUMENT_CHARS) {
      return document;
    }
    return document.substring(0, MAX_DOCUMENT_CHARS)
        + TRUNCATION_MARKER.formatted(document.length());
  }
}
