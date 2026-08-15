package interview.guide.modules.interview.agent.model;

import interview.guide.modules.interview.agent.runtime.AgentLoopState;
import interview.guide.modules.interview.agent.runtime.AgentLoopStatus;
import interview.guide.modules.interview.agent.runtime.AnswerDepthLevel;
import interview.guide.modules.interview.agent.runtime.AnswerEvidence;
import interview.guide.modules.interview.agent.runtime.InterviewAgentLoop;
import interview.guide.modules.interview.agent.runtime.Turn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentInterviewSessionResponseTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("普通会话响应不向候选人暴露实时评级和证据")
  void shouldHideAssessmentAndEvidenceFromSessionResponse() throws Exception {
    AgentLoopState state = new AgentLoopState(
        "sid",
        InterviewAgentLoop.RUNTIME_VERSION,
        "JD",
        "Resume",
        1,
        6,
        null,
        List.of(new Turn(
            1,
            "你如何处理缓存一致性？",
            "我使用延迟双删",
            AnswerDepthLevel.L3,
            new AnswerEvidence("说明了具体策略", "我使用延迟双删")
        )),
        AgentLoopStatus.COMPLETED,
        "完成"
    );

    String json = objectMapper.writeValueAsString(AgentInterviewSessionResponse.from(state));

    assertThat(json)
        .contains("你如何处理缓存一致性？")
        .contains("我使用延迟双删")
        .doesNotContain("assessment")
        .doesNotContain("evidence")
        .doesNotContain("L3")
        .doesNotContain("说明了具体策略");
  }
}
