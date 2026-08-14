package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.List;
import java.util.Map;

public record PracticeRecommendationFacts(
    List<PracticeDimensionFacts> dimensions,
    Map<Integer, PracticeQuestionFacts> questionsByTurn
) {}
