package interview.guide.modules.interview.agent.adaptive.memory;

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
  @DisplayName("长期记忆只进入规划上下文且不进入当前维度面试官上下文")
  void shouldKeepLongTermMemoryOutOfInterviewerContext() {
    assertThat(componentNames(PlannerContext.class))
        .contains("coveredTopics", "unverifiedClaims");
    assertThat(componentNames(InterviewerContext.class))
        .doesNotContain("coveredTopics", "unverifiedClaims");
  }

  @Test
  @DisplayName("面试官可以接收追问缺口但不得接收评级结论")
  void shouldAllowProbeGapsButNotRatingsInInterviewerContext() {
    assertThat(componentNames(InterviewerContext.class))
        .contains("currentDimensionAnswer", "currentAnswerGaps")
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
}
