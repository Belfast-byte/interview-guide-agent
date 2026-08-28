package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import java.time.LocalDateTime;
import java.util.List;

public sealed interface SemanticState
    permits EvaluationSemanticState, PracticeSemanticState {

  SemanticStateKey key();

  long revision();

  List<StablePattern> stablePatterns();

  LocalDateTime updatedAt();
}
