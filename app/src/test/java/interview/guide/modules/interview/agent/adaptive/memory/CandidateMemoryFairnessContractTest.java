package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentContext;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentRequest;
import interview.guide.modules.interview.agent.adaptive.core.context.EpisodePromptFact;
import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerContext;
import interview.guide.modules.interview.agent.adaptive.core.context.PlannerContext;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateMemoryClaimEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateMemoryClaimStatus;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateMemoryTopicEntity;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateMemoryFairnessContractTest {

  @Test
  @DisplayName("M5 前候选人长期记忆 schema 物理不存在评级字段")
  void shouldKeepRatingFieldsOutOfLongTermMemorySchema() {
    assertThat(Stream.concat(
        Arrays.stream(CandidateMemoryTopicEntity.class.getDeclaredFields()),
        Arrays.stream(CandidateMemoryClaimEntity.class.getDeclaredFields())
    ).map(Field::getName).map(name -> name.toLowerCase(Locale.ROOT)))
        .noneMatch(name -> name.contains("score")
            || name.contains("rating")
            || name.contains("level")
            || name.contains("evidence"));
  }

  @Test
  @DisplayName("候选人声明在 M5 前只能保持未验证状态")
  void shouldKeepClaimsUnverified() {
    assertThat(CandidateMemoryClaimStatus.values())
        .containsExactly(CandidateMemoryClaimStatus.UNVERIFIED);
  }

  @Test
  @DisplayName("长期记忆不进入正式规划上下文和当前维度面试官上下文")
  void shouldKeepLongTermMemoryOutOfInterviewerContext() {
    assertThat(componentNames(PlannerContext.class))
        .doesNotContain("coveredTopics", "unverifiedClaims");
    assertThat(componentNames(InterviewerContext.class))
        .doesNotContain("coveredTopics", "unverifiedClaims");
  }

  @Test
  @DisplayName("Interviewer 的跨场 Episode 输入严格限定为六个白名单字段")
  void shouldWhitelistEpisodePromptFields() {
    assertThat(componentNames(EpisodePromptFact.class)).containsExactly(
        "skillId",
        "focusId",
        "depthLevel",
        "errorTags",
        "answerHabitTags",
        "createdAt"
    );
    assertThat(componentNames(InterviewerContext.class))
        .contains("episodeHistory")
        .doesNotContain("completedDimensionBriefs", "profiles", "counters");
    assertThat(componentTypeNames(InterviewerContext.class))
        .noneMatch(type -> type.contains("DimensionBrief"));
  }

  @Test
  @DisplayName("Planner 与 Assessment 请求结构不接收 Episode Profile Counter 或标签")
  void shouldKeepHistoricalMemoryOutOfPlannerAndAssessment() {
    assertThat(componentNames(PlannerContext.class))
        .doesNotContain("episodeHistory", "profiles", "counters", "tags");
    assertThat(componentNames(AssessmentRequest.class))
        .containsExactly("sessionId", "turnIndex", "context", "skillReferenceSection");
    assertThat(componentNames(AssessmentContext.class))
        .containsExactly("dimension", "focus", "question", "answer", "toolResult", "rubric");
    assertThat(Stream.concat(
        Arrays.stream(componentNames(AssessmentRequest.class)),
        Arrays.stream(componentNames(AssessmentContext.class))
    )).noneMatch(this::isHistoricalMemoryField);
  }

  @Test
  @DisplayName("面试官可以接收追问缺口但不得接收评级结论")
  void shouldAllowProbeGapsButNotRatingsInInterviewerContext() {
    assertThat(componentNames(InterviewerContext.class))
        .contains("currentDimensionAnswer", "workingMemory")
        .doesNotContain(
            "depthLevel",
            "confidence",
            "rationaleSummary",
            "recommendSwitchQuestion",
            "evidenceQuotes"
        );
  }

  private String[] componentNames(Class<? extends Record> type) {
    return Arrays.stream(type.getRecordComponents())
        .map(component -> component.getName())
        .toArray(String[]::new);
  }

  private Stream<String> componentTypeNames(Class<? extends Record> type) {
    return Arrays.stream(type.getRecordComponents())
        .map(component -> component.getGenericType().getTypeName());
  }

  private boolean isHistoricalMemoryField(String field) {
    String normalized = field.toLowerCase(Locale.ROOT);
    return normalized.contains("episode")
        || normalized.contains("profile")
        || normalized.contains("counter")
        || normalized.contains("tag")
        || normalized.contains("brief");
  }
}
