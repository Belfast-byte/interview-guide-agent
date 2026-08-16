package interview.guide.modules.interview.agent.adaptive.assessment;

/**
 * 练习推荐事实来源接口。
 */
public interface PracticeRecommendationFactsSource {

  PracticeRecommendationFacts load(String sessionId);
}
