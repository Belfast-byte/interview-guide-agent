package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.context.CoveredTopic;
import interview.guide.modules.interview.agent.adaptive.core.context.DimensionBrief;
import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerContext;
import interview.guide.modules.interview.agent.adaptive.core.context.PlannerContext;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.PlanningSkill;
import interview.guide.modules.interview.agent.adaptive.core.context.UnverifiedClaim;
import interview.guide.modules.interview.agent.adaptive.core.context.CandidateClaimType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextAssemblerTest {

  private final ContextAssembler assembler = new ContextAssembler();

  @Test
  @DisplayName("规划上下文包含职位事实、稳定技能目录和已覆盖主题")
  void shouldExposeGovernedPlanningInputsToPlanner() {
    List<CoveredTopic> topics = List.of(new CoveredTopic("java-backend", "REDIS"));
    List<PlanningSkill> skills = List.of(new PlanningSkill(
        "java-backend",
        List.of("JAVA", "REDIS")
    ));
    List<UnverifiedClaim> claims = List.of(new UnverifiedClaim(
        CandidateClaimType.PROJECT_EXPERIENCE,
        "java-backend",
        "REDIS"
    ));
    PlannerContext context = assembler.planner("JD", "Resume", topics, claims, skills);

    assertThat(context).isEqualTo(new PlannerContext(
        "JD", "Resume", topics, claims, skills
    ));
  }

  @Test
  @DisplayName("面试官只读取目标维度的原始轮次")
  void shouldExposeOnlyTargetDimensionTurnsToInterviewer() {
    AdaptiveInterviewTurn previousDimension = turn(1, 0, "上一维度问题", "上一维度回答");
    AdaptiveInterviewTurn currentDimension = turn(2, 1, "当前维度问题", null);
    CandidateAnswer answer = new CandidateAnswer(2, "当前维度回答");

    InterviewerContext context = assembler.interviewer(
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
        List.of()
    );

    assertThat(context.currentDimensionTurns()).containsExactly(currentDimension);
    assertThat(context.currentDimensionAnswer()).isEqualTo(answer);
    assertThat(context.currentTurn()).isEqualTo(2);
  }

  @Test
  @DisplayName("同维度追问缺口随当前回答进入面试官上下文")
  void shouldExposeCurrentAnswerGapsForSameDimension() {
    AdaptiveInterviewTurn currentDimension = turn(2, 1, "当前维度问题", null);
    CandidateAnswer answer = new CandidateAnswer(2, "当前维度回答");
    ProbeGap gap = new ProbeGap("当前维度回答", "未说明失败场景");

    InterviewerContext context = assembler.interviewer(
        "JD",
        "Resume",
        6,
        1,
        "项目经验",
        "架构取舍",
        List.of(),
        null,
        List.of(turn(1, 0, "上一维度问题", "上一维度回答"), currentDimension),
        answer,
        List.of(gap),
        List.of()
    );

    assertThat(context.currentAnswerGaps()).containsExactly(gap);
  }

  @Test
  @DisplayName("切换维度时清空上一维度的追问缺口")
  void shouldClearProbeGapsAfterDimensionSwitch() {
    AdaptiveInterviewTurn answeredTurn = turn(1, 0, "专业基础问题", null);
    ProbeGap gap = new ProbeGap("上一维度回答", "未说明失败场景");

    InterviewerContext context = assembler.interviewer(
        "JD",
        "Resume",
        6,
        1,
        "项目经验",
        "架构取舍",
        List.of(),
        null,
        List.of(answeredTurn),
        new CandidateAnswer(1, "上一维度回答"),
        List.of(gap),
        List.of()
    );

    assertThat(context.currentAnswerGaps()).isEmpty();
  }

  @Test
  @DisplayName("切换维度时不把上一维度回答泄漏给新面试官")
  void shouldExcludePreviousDimensionAnswerAfterDimensionSwitch() {
    AdaptiveInterviewTurn answeredTurn = turn(1, 0, "专业基础问题", null);
    DimensionBrief completedBrief = new DimensionBrief(
        "session-1",
        0,
        "专业基础",
        "缓存",
        "讨论了缓存一致性的方案与取舍",
        List.of(1)
    );

    InterviewerContext context = assembler.interviewer(
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
        List.of(completedBrief)
    );

    assertThat(context.currentDimensionTurns()).isEmpty();
    assertThat(context.currentDimensionAnswer()).isNull();
    assertThat(context.completedDimensionBriefs()).containsExactly(completedBrief);
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
}
