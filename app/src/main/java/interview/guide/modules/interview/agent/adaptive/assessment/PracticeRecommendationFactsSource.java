package interview.guide.modules.interview.agent.adaptive.assessment;

public interface PracticeRecommendationFactsSource {

  PracticeRecommendationFacts load(String sessionId);
}
