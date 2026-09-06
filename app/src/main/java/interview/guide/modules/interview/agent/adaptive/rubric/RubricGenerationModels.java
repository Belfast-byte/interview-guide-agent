package interview.guide.modules.interview.agent.adaptive.rubric;

import java.util.List;

public interface RubricGenerationModels {
  Draft generate(String dimension, String focus, String question);
  Review judge(Draft draft);
  String generatorProvider();
  String judgeProvider();

  record Draft(String question, String topic, String rubric, List<String> keyPoints) {
    public Draft { keyPoints = keyPoints == null ? List.of() : List.copyOf(keyPoints); }
    public boolean valid() {
      return question != null && !question.isBlank() && question.length() <= 6000
          && topic != null && !topic.isBlank() && topic.length() <= 300
          && rubric != null && !rubric.isBlank() && rubric.length() <= 16000
          && java.util.stream.IntStream.rangeClosed(0, 4).allMatch(i -> rubric.contains("L" + i))
          && !keyPoints.isEmpty() && keyPoints.size() <= 20
          && keyPoints.stream().allMatch(k -> k != null && !k.isBlank() && k.length() <= 1000);
    }
  }
  record Review(boolean approved, boolean factuallyCorrect, boolean levelAnchorsClear,
      boolean alternativesAccepted, boolean noPrivateContent, String rationale, List<String> issues) {
    public Review { issues = issues == null ? List.of() : List.copyOf(issues); }
    public boolean passes() {
      return approved && factuallyCorrect && levelAnchorsClear && alternativesAccepted
          && noPrivateContent && rationale != null && !rationale.isBlank()
          && rationale.length() <= 2000 && issues.isEmpty();
    }
  }
}
