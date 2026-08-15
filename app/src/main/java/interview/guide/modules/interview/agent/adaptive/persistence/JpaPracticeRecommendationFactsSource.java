package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.modules.interview.agent.adaptive.assessment.PracticeAssessmentFacts;
import interview.guide.modules.interview.agent.adaptive.assessment.PracticeDimensionFacts;
import interview.guide.modules.interview.agent.adaptive.assessment.PracticeQuestionFacts;
import interview.guide.modules.interview.agent.adaptive.assessment.PracticeRecommendationFacts;
import interview.guide.modules.interview.agent.adaptive.assessment.PracticeRecommendationFactsSource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class JpaPracticeRecommendationFactsSource
    implements PracticeRecommendationFactsSource {

  private final AdaptiveAgentPlanRepository planRepository;
  private final AdaptiveAgentTurnRepository turnRepository;
  private final AdaptiveAgentAssessmentRepository assessmentRepository;

  public JpaPracticeRecommendationFactsSource(
      AdaptiveAgentPlanRepository planRepository,
      AdaptiveAgentTurnRepository turnRepository,
      AdaptiveAgentAssessmentRepository assessmentRepository
  ) {
    this.planRepository = planRepository;
    this.turnRepository = turnRepository;
    this.assessmentRepository = assessmentRepository;
  }

  @Override
  public PracticeRecommendationFacts load(String sessionId) {
    List<AdaptiveAgentAssessmentEntity> assessments = assessmentRepository
        .findBySessionIdOrderByDimensionOrderAscTurnIndexAsc(sessionId);
    Map<Integer, PracticeQuestionFacts> questionsByTurn = turnRepository
        .findBySessionIdOrderByTurnIndex(sessionId).stream()
        .collect(Collectors.toMap(
            AdaptiveAgentTurnEntity::turnIndex,
            turn -> new PracticeQuestionFacts(
                turn.turnIndex(),
                turn.questionSourceId(),
                turn.questionDifficulty()
            )
        ));
    List<PracticeDimensionFacts> dimensions = planRepository
        .findBySessionIdOrderByDimensionOrder(sessionId).stream()
        .map(plan -> new PracticeDimensionFacts(
            plan.dimensionOrder(),
            plan.dimension(),
            plan.focus(),
            assessments.stream()
                .filter(assessment ->
                    assessment.dimensionOrder() == plan.dimensionOrder())
                .map(assessment -> new PracticeAssessmentFacts(
                    assessment.turnIndex(),
                    assessment.depthLevel()
                ))
                .toList()
        ))
        .toList();
    return new PracticeRecommendationFacts(dimensions, questionsByTurn);
  }
}
