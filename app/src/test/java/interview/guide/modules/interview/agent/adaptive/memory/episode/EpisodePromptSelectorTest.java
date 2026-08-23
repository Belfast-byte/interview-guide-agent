package interview.guide.modules.interview.agent.adaptive.memory.episode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.EpisodePromptFact;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import tools.jackson.databind.ObjectMapper;

class EpisodePromptSelectorTest {

  private static final TopicKey CURRENT_TOPIC = new TopicKey("java-backend", "REDIS");
  private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 8, 23, 12, 0);

  @Test
  @DisplayName("同 TopicKey 优先于同 skill，组内按创建时间和 ID 倒序")
  void shouldSelectInStablePriorityOrder() {
    EpisodePromptSelector selector = selector(estimator(json -> 0));

    List<EpisodePromptFact> selected = selector.select(CURRENT_TOPIC, List.of(
        candidate(3, "JVM", 3),
        candidate(1, "REDIS", 1),
        candidate(4, "JVM", 4),
        candidate(2, "REDIS", 2),
        new EpisodePromptCandidate(5, new EpisodePromptFact(
            "frontend",
            "REACT",
            DepthLevel.L2,
            List.of(),
            List.of(),
            BASE_TIME.plusMinutes(5)
        ))
    ));

    assertThat(selected).extracting(EpisodePromptFact::focusId)
        .containsExactly("REDIS", "REDIS", "JVM", "JVM");
    assertThat(selected).extracting(EpisodePromptFact::createdAt)
        .containsExactly(
            BASE_TIME.plusMinutes(2),
            BASE_TIME.plusMinutes(1),
            BASE_TIME.plusMinutes(4),
            BASE_TIME.plusMinutes(3)
        );
  }

  @Test
  @DisplayName("相同创建时间时使用持久化 ID 保持确定性倒序")
  void shouldUseSourceIdAsStableTieBreaker() {
    EpisodePromptSelector selector = selector(estimator(json -> 0));
    EpisodePromptCandidate olderId = new EpisodePromptCandidate(7, fact(
        "REDIS",
        DepthLevel.L1,
        BASE_TIME
    ));
    EpisodePromptCandidate newerId = new EpisodePromptCandidate(
        8,
        fact("REDIS", DepthLevel.L3, BASE_TIME)
    );

    assertThat(selector.select(CURRENT_TOPIC, List.of(olderId, newerId)))
        .extracting(EpisodePromptFact::depthLevel)
        .containsExactly(DepthLevel.L3, DepthLevel.L1);
  }

  @Test
  @DisplayName("完整 JSON 数组恰好 2000 tokens 时允许，超过时拒绝")
  void shouldHonorInclusiveTokenBoundary() {
    TokenCountEstimator estimator = estimator(json -> episodeCount(json) * 1_000);
    EpisodePromptSelector selector = selector(estimator);

    List<EpisodePromptFact> selected = selector.select(CURRENT_TOPIC, List.of(
        candidate(1, "REDIS", 1),
        candidate(2, "REDIS", 2),
        candidate(3, "REDIS", 3)
    ));

    assertThat(selected).hasSize(2);
  }

  @Test
  @DisplayName("单条超限时跳过并继续选择后续可容纳事实")
  void shouldSkipOversizedFactAndContinue() {
    TokenCountEstimator estimator = estimator(json -> json.contains("OVERSIZED")
        ? EpisodePromptSelector.MAX_PROMPT_TOKENS + 1
        : 1);
    EpisodePromptSelector selector = selector(estimator);

    List<EpisodePromptFact> selected = selector.select(CURRENT_TOPIC, List.of(
        candidate(3, "REDIS", 3),
        candidate(2, "OVERSIZED", 2),
        candidate(1, "JVM", 1)
    ));

    assertThat(selected).extracting(EpisodePromptFact::focusId)
        .containsExactly("REDIS", "JVM");
  }

  private EpisodePromptSelector selector(TokenCountEstimator estimator) {
    return new EpisodePromptSelector(new ObjectMapper(), estimator);
  }

  private TokenCountEstimator estimator(ToIntFunction<String> estimate) {
    TokenCountEstimator estimator = mock(TokenCountEstimator.class);
    when(estimator.estimate(anyString()))
        .thenAnswer(invocation -> estimate.applyAsInt(invocation.getArgument(0)));
    return estimator;
  }

  private EpisodePromptCandidate candidate(long id, String focusId, int minute) {
    return new EpisodePromptCandidate(id, fact(
        focusId,
        DepthLevel.L2,
        BASE_TIME.plusMinutes(minute)
    ));
  }

  private EpisodePromptFact fact(
      String focusId,
      DepthLevel depthLevel,
      LocalDateTime createdAt
  ) {
    return new EpisodePromptFact(
        "java-backend",
        focusId,
        depthLevel,
        List.of("MISSING_FAILURE_BOUNDARY"),
        List.of("STRUCTURED_REASONING"),
        createdAt
    );
  }

  private static int episodeCount(String json) {
    return json.split("skillId", -1).length - 1;
  }
}
