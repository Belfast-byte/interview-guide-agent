package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PracticeMemoryService {

  private final SemanticStateSource stateSource;

  public PracticePlanningMemory planning(MemoryOwner owner, PracticeScope scope) {
    Map<TopicKey, TopicStates> statesByTopic = new LinkedHashMap<>();
    stateSource.findByOwner(owner).stream()
        .filter(state -> scope.topics().contains(state.key().topic()))
        .forEach(state -> statesByTopic.computeIfAbsent(
            state.key().topic(), ignored -> new TopicStates()).add(state));
    List<PracticePlanningTopic> topics = scope.topics().stream()
        .map(topic -> statesByTopic.getOrDefault(topic, new TopicStates()).view(topic))
        .toList();
    return new PracticePlanningMemory(topics);
  }

  private static final class TopicStates {

    private EvaluationSemanticState evaluation;
    private PracticeSemanticState practice;

    void add(SemanticState state) {
      if (state instanceof EvaluationSemanticState value) {
        evaluation = value;
      } else {
        practice = (PracticeSemanticState) state;
      }
    }

    PracticePlanningTopic view(TopicKey topic) {
      List<StablePattern> patterns = new ArrayList<>();
      if (evaluation != null) {
        patterns.addAll(evaluation.stablePatterns());
      }
      if (practice != null) {
        patterns.addAll(practice.stablePatterns());
      }
      return new PracticePlanningTopic(
          topic,
          new PracticePlanningStatus(
              evaluation == null ? null : evaluation.ability(),
              practice == null ? null : practice.mastery(),
              practice == null ? null : practice.transfer().status()
          ),
          patterns.stream().distinct().toList()
      );
    }
  }
}
