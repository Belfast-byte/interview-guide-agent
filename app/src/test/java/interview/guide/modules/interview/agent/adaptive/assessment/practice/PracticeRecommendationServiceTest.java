package interview.guide.modules.interview.agent.adaptive.assessment.practice;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.planning.PlanDimensionStatus;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankQuestion;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeRecommendationServiceTest {

  @Test
  @DisplayName("最低深度维度只推荐同难度且本场未使用的题目")
  void shouldRecommendUnusedQuestionAtSameDifficulty() {
    PracticeRecommendationFacts facts = new PracticeRecommendationFacts(
        List.of(
            new PracticeDimensionFacts(
                0,
                "架构设计",
                "缓存权衡",
                List.of(new PracticeAssessmentFacts(1, DepthLevel.L2))
            ),
            new PracticeDimensionFacts(
                1,
                "问题解决",
                "定位过程",
                List.of()
            )
        ),
        Map.of(
            1, new PracticeQuestionFacts(1, "question:1", "MEDIUM"),
            2, new PracticeQuestionFacts(2, "question:2", "EASY")
        )
    );
    PracticeRecommendationService service = new PracticeRecommendationService(
        sessionId -> facts,
        (query, difficulty) -> List.of(
            question("question:2", "EASY", "本场原题？"),
            question("question:3", "MEDIUM", "错误难度？"),
            question("question:4", "EASY", "新的同难度练习题？")
        )
    );

    List<PracticeRecommendation> recommendations = service.recommend(
        "session-1",
        currentDimension(),
        new AssessmentDecision(
            "session-1",
            2,
            DepthLevel.L1,
            0.8,
            "只复述了步骤",
            false,
            List.of("回答")
        )
    );

    assertThat(recommendations).containsExactly(new PracticeRecommendation(
        1,
        "问题解决",
        DepthLevel.L1,
        "question:4",
        "EASY",
        "新的同难度练习题？",
        PracticeStatus.PENDING
    ));
  }

  @Test
  @DisplayName("最低维度没有已验证题目难度时不猜测练习难度")
  void shouldNotGuessDifficultyWithoutQuestionProvenance() {
    AtomicInteger searches = new AtomicInteger();
    PracticeRecommendationFacts facts = new PracticeRecommendationFacts(
        List.of(new PracticeDimensionFacts(
            0,
            "架构设计",
            "缓存权衡",
            List.of()
        )),
        Map.of(1, new PracticeQuestionFacts(1, null, null))
    );
    PracticeRecommendationService service = new PracticeRecommendationService(
        sessionId -> facts,
        (query, difficulty) -> {
          searches.incrementAndGet();
          return List.of();
        }
    );

    assertThat(service.recommend(
        "session-1",
        new PlannedDimension(
            0,
            "架构设计",
            "缓存权衡",
            "ARCHITECTURE",
            1,
            List.of(),
            null,
            1,
            0,
            PlanDimensionStatus.IN_PROGRESS
        ),
        new AssessmentDecision(
            "session-1",
            1,
            DepthLevel.L1,
            0.8,
            "只复述概念",
            false,
            List.of("回答")
        )
    )).isEmpty();
    assertThat(searches).hasValue(0);
  }

  private PlannedDimension currentDimension() {
    return new PlannedDimension(
        1,
        "问题解决",
        "定位过程",
        "PROBLEM_SOLVING",
        1,
        List.of(),
        null,
        1,
        0,
        PlanDimensionStatus.IN_PROGRESS
    );
  }

  private QuestionBankQuestion question(
      String stableId,
      String difficulty,
      String content
  ) {
    return new QuestionBankQuestion(
        stableId,
        Long.valueOf(stableId.substring(stableId.indexOf(':') + 1)),
        "category",
        difficulty,
        content
    );
  }
}
