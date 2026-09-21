package interview.guide.modules.interview.agent.adaptive.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;

import interview.guide.modules.interview.agent.adaptive.planning.PlannerContext;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningSkill;
import interview.guide.modules.interview.agent.adaptive.core.session.CandidateLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.skill.InterviewSkillService;
import interview.guide.modules.interview.agent.adaptive.core.context.*;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContextAssemblerTest {

  private final ContextAssembler assembler = new ContextAssembler(mock(InterviewSkillService.class), new interview.guide.modules.interview.agent.adaptive.runtime.AdaptiveAgentRuntimeConfiguration.QueryTools(List.of()));

  @Test
  void decisionUsesShortInstructionsOncePerSkillWithoutLoadingAssessmentReference() {
    var skills = mock(InterviewSkillService.class);
    when(skills.decisionInstructions("java-backend")).thenReturn("岗位短策略");
    var dimension = new PlannedDimension(new CapabilityTarget(new CapabilityTarget.Identity(
        0, "Java", "并发", new TopicKey("java-backend", "JAVA")), new CapabilityTarget.Budget(2, 2),
        new CapabilityTarget.Depth(DepthLevel.L2, DepthLevel.L4), List.of()));
    var context = new ContextAssembler(skills, new interview.guide.modules.interview.agent.adaptive.runtime.AdaptiveAgentRuntimeConfiguration.QueryTools(List.of(org.springframework.ai.support.ToolCallbacks.from(new Queries())))).agent(new ContextAssembler.AgentContextInput(
        new MemoryOwner(null, "candidate"), "session", "provider", SessionMode.EVALUATION, 12,
        List.of(dimension, dimension), new CoverageView(0, 12, List.of(), List.of(), List.of()),
        List.of(), WorkingMemory.empty()));
    assertThat(context.facts().skillGuidance()).containsExactly(new AgentContext.SkillGuidance("java-backend", "岗位短策略"));
    assertThat(context.facts().allowedReadTools()).containsExactly("test_query");
    verify(skills).decisionInstructions("java-backend");
    verify(skills, never()).buildEvaluationReferenceSection(anyString());
  }

  static class Queries {
    @org.springframework.ai.tool.annotation.Tool(name = "test_query", description = "test")
    public String read() { return "unused"; }
  }

  @Test
  @DisplayName("规划上下文只包含本次会话输入和稳定技能目录")
  void shouldExposeGovernedPlanningInputsToPlanner() {
    List<PlanningSkill> skills = List.of(new PlanningSkill(
        "java-backend",
        List.of("JAVA", "REDIS")
    ));
    PlannerContext context = assembler.planner(new PlannerContext(
        "JD", "Resume", SessionMode.EVALUATION, CandidateLevel.CAMPUS, List.of(), skills
    ));

    assertThat(context).isEqualTo(new PlannerContext(
        "JD", "Resume", SessionMode.EVALUATION, CandidateLevel.CAMPUS, List.of(), skills
    ));
  }

  @Test
  @DisplayName("规划文档保留原文，由输入预算明确报告超限")
  void shouldPreserveFullDocuments() {
    String longDocument = "岗位要求：熟悉分布式系统。".repeat(800);

    PlannerContext context = assembler.planner(new PlannerContext(
        longDocument,
        "三行简历",
        SessionMode.EVALUATION,
        CandidateLevel.CAMPUS,
        List.of(),
        List.of()
    ));

    assertThat(context.jd()).isEqualTo(longDocument);
    assertThat(context.resume()).isEqualTo("三行简历");
  }
}
