package interview.guide.modules.interview.agent.adaptive.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import interview.guide.modules.interview.agent.adaptive.planning.PlannerContext;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningSkill;
import interview.guide.modules.interview.agent.adaptive.core.session.CandidateLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.skill.InterviewSkillService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContextAssemblerTest {

  private final ContextAssembler assembler = new ContextAssembler(mock(InterviewSkillService.class));

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
  @DisplayName("规划文档超长时截断并标注")
  void shouldTruncateLongDocumentsWithMarker() {
    String longDocument = "岗位要求：熟悉分布式系统。".repeat(800);

    PlannerContext context = assembler.planner(new PlannerContext(
        longDocument,
        "三行简历",
        SessionMode.EVALUATION,
        CandidateLevel.CAMPUS,
        List.of(),
        List.of()
    ));

    assertThat(context.jd())
        .startsWith(longDocument.substring(0, 50))
        .endsWith("已截断]")
        .hasSizeLessThan(longDocument.length());
    assertThat(context.resume()).isEqualTo("三行简历");
  }
}
