package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerContext;
import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerWorkView;
import interview.guide.modules.interview.agent.adaptive.core.context.PlannerContext;
import interview.guide.modules.interview.agent.adaptive.core.context.PlanningSkill;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.CandidateLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextAssemblerTest {

  private final ContextAssembler assembler = new ContextAssembler();

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
  @DisplayName("面试官只读取目标维度的原始轮次")
  void shouldExposeOnlyTargetDimensionTurnsToInterviewer() {
    AdaptiveInterviewTurn previousDimension = turn(1, 0, "上一维度问题", "上一维度回答");
    AdaptiveInterviewTurn currentDimension = turn(2, 1, "当前维度问题", null);
    CandidateAnswer answer = new CandidateAnswer(2, "当前维度回答");

    InterviewerContext context = assembler.interviewer(new InterviewerContextInput(
        "JD",
        "Resume",
        6,
        1,
        "项目经验",
        "架构取舍",
        List.of("question_bank_search"),
        null,
        List.of(previousDimension, currentDimension),
        answer,
        plannedMemory(3, "java-backend", "PROJECT"),
        null,
        null
    ));

    assertThat(context.currentDimensionTurns()).containsExactly(currentDimension);
    assertThat(context.currentDimensionAnswer()).isEqualTo(answer);
    assertThat(context.currentTurn()).isEqualTo(2);
  }

  @Test
  @DisplayName("切换维度时清除上一维度回答")
  void shouldExcludePreviousAnswer() {
    AdaptiveInterviewTurn answeredTurn = turn(1, 0, "专业基础问题", null);

    InterviewerContext context = assembler.interviewer(new InterviewerContextInput(
        "JD",
        "Resume",
        6,
        1,
        "项目经验",
        "架构取舍",
        List.of(),
        null,
        List.of(answeredTurn),
        new CandidateAnswer(1, "包含敏感锚定内容的上一维度回答"),
        plannedMemory(2, "java-backend", "PROJECT"),
        null,
        null
    ));

    assertThat(context.currentDimensionTurns()).isEmpty();
    assertThat(context.currentDimensionAnswer()).isNull();
  }

  @Test
  @DisplayName("JD 和简历超长时截断并标注，短文档原样注入")
  void shouldTruncateLongDocumentsWithMarker() {
    String longJd = "岗位要求：熟悉分布式系统。".repeat(800);
    String shortResume = "三行简历";

    PlannerContext plannerContext = assembler.planner(new PlannerContext(
        longJd,
        longJd,
        SessionMode.EVALUATION,
        CandidateLevel.CAMPUS,
        List.of(),
        List.of()
    ));
    InterviewerContext interviewerContext = assembler.interviewer(new InterviewerContextInput(
        longJd, shortResume, 6, 0, "专业基础", "缓存与并发",
        List.of(), "java-backend", List.of(), null,
        plannedMemory(1, "java-backend", "CACHE"), null, null
    ));

    assertThat(plannerContext.jd())
        .startsWith(longJd.substring(0, 50))
        .endsWith("已截断]")
        .hasSizeLessThan(longJd.length());
    assertThat(interviewerContext.jd()).endsWith("已截断]");
    assertThat(interviewerContext.resume()).isEqualTo("三行简历");
  }

  private AdaptiveInterviewTurn turn(
      int turnIndex,
      int dimensionOrder,
      String question,
      String answer
  ) {
    return new AdaptiveInterviewTurn(
        turnIndex,
        dimensionOrder,
        question,
        "提问原因",
        answer,
        AgentResponseType.ASK,
        question,
        "决策原因"
    );
  }

  private InterviewerWorkView plannedMemory(
      int turnIndex,
      String skillId,
      String focusId
  ) {
    return new InterviewerWorkView(
        "target-0",
        new TopicKey(skillId, focusId),
        DepthLevel.L2,
        DepthLevel.L3,
        DepthLevel.L0,
        "当前焦点",
        null,
        6 - turnIndex,
        2,
        1
    );
  }
}
