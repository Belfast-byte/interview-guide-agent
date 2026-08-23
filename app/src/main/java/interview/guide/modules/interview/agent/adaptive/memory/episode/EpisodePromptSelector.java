package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 按固定优先级在历史 Episode 专属 token 预算内选择 Prompt 事实。
 */
@Component
@RequiredArgsConstructor
public class EpisodePromptSelector {

  static final int MAX_PROMPT_TOKENS = 2_000;

  private final ObjectMapper objectMapper;
  private final TokenCountEstimator tokenEstimator;

  public List<EpisodePromptFact> select(
      TopicKey currentTopic,
      List<EpisodePromptCandidate> candidates
  ) {
    List<EpisodePromptFact> selected = new ArrayList<>();
    candidates.stream()
        .sorted(orderFor(currentTopic))
        .map(EpisodePromptCandidate::fact)
        .forEach(fact -> appendIfFits(selected, fact));
    return List.copyOf(selected);
  }

  private void appendIfFits(
      List<EpisodePromptFact> selected,
      EpisodePromptFact fact
  ) {
    List<EpisodePromptFact> proposed = new ArrayList<>(selected);
    proposed.add(fact);
    if (tokenEstimator.estimate(toJson(proposed)) <= MAX_PROMPT_TOKENS) {
      selected.add(fact);
    }
  }

  private Comparator<EpisodePromptCandidate> orderFor(TopicKey currentTopic) {
    return Comparator
        .comparing((EpisodePromptCandidate candidate) ->
            !sameTopic(candidate.fact(), currentTopic))
        .thenComparing(
            candidate -> candidate.fact().createdAt(),
            Comparator.reverseOrder()
        )
        .thenComparing(EpisodePromptCandidate::sourceId, Comparator.reverseOrder());
  }

  private boolean sameTopic(EpisodePromptFact fact, TopicKey topic) {
    return fact.skillId().equals(topic.skillId())
        && fact.focusId().equals(topic.focusId());
  }

  private String toJson(List<EpisodePromptFact> facts) {
    try {
      return objectMapper.writeValueAsString(facts);
    } catch (JacksonException e) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Episode prompt 序列化失败",
          e
      );
    }
  }
}
