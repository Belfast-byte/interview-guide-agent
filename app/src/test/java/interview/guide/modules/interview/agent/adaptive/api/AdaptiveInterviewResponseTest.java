package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveSessionStatus;
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
            6
        ),
        "JD",
        "Resume",
        null,
        List.of(new AdaptiveInterviewTurn(
            1,
            "第一题？",
            "验证基础",
            null,
            null,
            null,
            "内部决策理由"
        ))
    );

    AdaptiveInterviewResponse response = AdaptiveInterviewResponse.from(history);

    assertThat(response.currentQuestion()).isEqualTo("第一题？");
    assertThat(response.turns()).containsExactly(
        new AdaptiveInterviewTurnResponse(1, "第一题？", null)
    );
  }
}
