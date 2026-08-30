package interview.guide.modules.interview.agent.adaptive.api;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testDimension;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveInterviewResponseTest {

  @Test
  @DisplayName("候选人响应不暴露内部决策理由")
  void shouldExposeCandidateViewOnly() {
    AdaptiveInterviewHistory history = new AdaptiveInterviewHistory(
        new AdaptiveInterviewSession(
            "session-1",
            AdaptiveInterviewSession.RUNTIME_VERSION,
            AdaptiveSessionStatus.IN_PROGRESS,
            1,
            6,
            EVALUATION_SETTINGS
        ),
        "candidate-1",
        "JD",
        "Resume",
        null,
        List.of(new AdaptiveInterviewTurn(
            1,
            0,
            "第一题？",
            "验证基础",
            null,
            null,
            null,
            "内部决策理由"
        ))
    );

    InterviewPlan plan = new InterviewPlan("session-1", 6, List.of(
            testDimension(new DimensionProposal(
                "专业基础",
                "缓存与并发",
                "REDIS",
                2,
                "java-backend"
            ), 0, 0)
        ));
    PlannedInterview interview = new PlannedInterview(
        history,
        plan,
        List.of()
    );

    AdaptiveInterviewResponse response = AdaptiveInterviewResponse.from(interview);

    assertThat(response.currentQuestion()).isEqualTo("第一题？");
    assertThat(response.currentTurn()).isEqualTo(1);
    assertThat(response.mode()).isEqualTo(EVALUATION_SETTINGS.mode());
    assertThat(response.candidateLevel()).isEqualTo(EVALUATION_SETTINGS.candidateLevel());
    assertThat(response.practiceScope()).isEmpty();
    assertThat(response.turns()).containsExactly(
        new AdaptiveInterviewTurnResponse(1, 0, "第一题？", null)
    );
    assertThat(response.dimensions()).extracting(AdaptiveInterviewDimensionResponse::dimension)
        .containsExactly("专业基础");
    assertThat(response.dimensions().getFirst().expectedDepth()).isEqualTo(DepthLevel.L2);
    assertThat(response.dimensions().getFirst().depthCeiling()).isEqualTo(DepthLevel.L3);
    assertThat(response.dimensions().getFirst().completedTurns()).isEqualTo(1);
  }

  @Test
  @DisplayName("空轮次历史映射出的当前问题为 null")
  void shouldExposeNullQuestionWhenNoTurns() {
    PlannedInterview interview = new PlannedInterview(
        new AdaptiveInterviewHistory(
            new AdaptiveInterviewSession(
                "session-1",
                AdaptiveInterviewSession.RUNTIME_VERSION,
                AdaptiveSessionStatus.CREATED,
                0,
                6,
                EVALUATION_SETTINGS
            ),
            "candidate-1",
            "JD",
            "Resume",
            null,
            List.of()
        ),
        new InterviewPlan("session-1", 0, List.of()),
        List.of()
    );

    AdaptiveInterviewResponse response = AdaptiveInterviewResponse.from(interview);

    assertThat(response.currentQuestion()).isNull();
    assertThat(response.turns()).isEmpty();
  }
}
