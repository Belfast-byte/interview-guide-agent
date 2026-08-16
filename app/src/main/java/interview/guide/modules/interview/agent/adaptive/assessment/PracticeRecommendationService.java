package interview.guide.modules.interview.agent.adaptive.assessment;

import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankQuestion;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankSearchSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 练习推荐服务，根据评估结果生成针对性练习建议。
 */
@Service
public class PracticeRecommendationService {

  private static final Comparator<RankedAssessment> FINAL_ASSESSMENT =
      Comparator.comparing(RankedAssessment::depthLevel)
          .thenComparingInt(RankedAssessment::turnIndex);

  private final PracticeRecommendationFactsSource factsSource;
  private final QuestionBankSearchSource questionBank;

  public PracticeRecommendationService(
      PracticeRecommendationFactsSource factsSource,
      QuestionBankSearchSource questionBank
  ) {
    this.factsSource = factsSource;
    this.questionBank = questionBank;
  }

  public List<PracticeRecommendation> recommend(
      String sessionId,
      PlannedDimension currentDimension,
      AssessmentDecision currentAssessment
  ) {
    PracticeRecommendationFacts facts = factsSource.load(sessionId);
    List<DimensionConclusion> conclusions = facts.dimensions().stream()
        .map(dimension -> conclusion(
            dimension,
            currentDimension,
            currentAssessment,
            facts
        ))
        .toList();
    DepthLevel weakestLevel = conclusions.stream()
        .map(DimensionConclusion::depthLevel)
        .min(DepthLevel::compareTo)
        .orElseThrow();
    Set<String> usedQuestionIds = new HashSet<>();
    facts.questionsByTurn().values().stream()
        .map(PracticeQuestionFacts::sourceId)
        .filter(sourceId -> sourceId != null)
        .forEach(usedQuestionIds::add);

    List<PracticeRecommendation> recommendations = new ArrayList<>();
    conclusions.stream()
        .filter(conclusion -> conclusion.depthLevel() == weakestLevel)
        .filter(conclusion -> conclusion.sourceQuestionId() != null)
        .forEach(conclusion -> questionBank.search(
                conclusion.dimension() + " " + conclusion.focus(),
                conclusion.questionDifficulty()
            ).stream()
            .filter(question -> conclusion.questionDifficulty().equals(
                question.difficulty()
            ))
            .filter(question -> !usedQuestionIds.contains(question.stableId()))
            .findFirst()
            .ifPresent(question -> {
              recommendations.add(recommendation(conclusion, question));
              usedQuestionIds.add(question.stableId());
            }));
    return List.copyOf(recommendations);
  }

  private DimensionConclusion conclusion(
      PracticeDimensionFacts dimension,
      PlannedDimension currentDimension,
      AssessmentDecision currentAssessment,
      PracticeRecommendationFacts facts
  ) {
    List<RankedAssessment> assessments = new ArrayList<>(
        dimension.assessments().stream()
            .map(assessment -> ranked(assessment, facts))
            .toList()
    );
    if (dimension.order() == currentDimension.order()) {
      PracticeQuestionFacts question = facts.questionsByTurn()
          .get(currentAssessment.turnIndex());
      assessments.add(new RankedAssessment(
          currentAssessment.turnIndex(),
          currentAssessment.depthLevel(),
          question.sourceId(),
          question.difficulty()
      ));
    }
    RankedAssessment finalAssessment = assessments.stream()
        .max(FINAL_ASSESSMENT)
        .orElseThrow();
    return new DimensionConclusion(
        dimension.order(),
        dimension.dimension(),
        dimension.focus(),
        finalAssessment.depthLevel(),
        finalAssessment.sourceQuestionId(),
        finalAssessment.questionDifficulty()
    );
  }

  private RankedAssessment ranked(
      PracticeAssessmentFacts assessment,
      PracticeRecommendationFacts facts
  ) {
    PracticeQuestionFacts question = facts.questionsByTurn()
        .get(assessment.turnIndex());
    return new RankedAssessment(
        assessment.turnIndex(),
        assessment.depthLevel(),
        question.sourceId(),
        question.difficulty()
    );
  }

  private PracticeRecommendation recommendation(
      DimensionConclusion conclusion,
      QuestionBankQuestion question
  ) {
    return new PracticeRecommendation(
        conclusion.order(),
        conclusion.dimension(),
        conclusion.depthLevel(),
        question.stableId(),
        question.difficulty(),
        question.question(),
        PracticeStatus.PENDING
    );
  }

  private record RankedAssessment(
      int turnIndex,
      DepthLevel depthLevel,
      String sourceQuestionId,
      String questionDifficulty
  ) {}

  private record DimensionConclusion(
      int order,
      String dimension,
      String focus,
      DepthLevel depthLevel,
      String sourceQuestionId,
      String questionDifficulty
  ) {}
}
