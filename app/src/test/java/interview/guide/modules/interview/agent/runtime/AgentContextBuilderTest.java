package interview.guide.modules.interview.agent.runtime;

import interview.guide.modules.interview.agent.tool.InterviewSkillCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentContextBuilderTest {

  @Mock
  private AgentInterviewPersistenceService persistenceService;

  @Mock
  private InterviewSkillCatalog skillCatalog;

  @Test
  @DisplayName("评估上下文只包含问答原文，不携带历史评级和证据")
  void shouldExcludeHistoricalAssessmentsFromAssessmentContext() {
    Turn completed = new Turn(
        1,
        "你如何处理缓存一致性？",
        "我使用延迟双删",
        AnswerDepthLevel.L4,
        new AnswerEvidence("历史结论", "我使用延迟双删")
    );
    Turn current = new Turn(2, "第二次删除失败怎么办？", null);
    AgentLoopState snapshot = new AgentLoopState(
        "sid",
        InterviewAgentLoop.RUNTIME_VERSION,
        "JD",
        "Resume",
        2,
        6,
        null,
        List.of(completed, current),
        AgentLoopStatus.IN_PROGRESS,
        null
    );
    when(persistenceService.get("sid")).thenReturn(snapshot);
    AgentContextBuilder builder = new AgentContextBuilder(
        persistenceService,
        skillCatalog
    );

    AssessmentContext context = builder.buildAssessment("sid", "通过消息重试补偿");

    assertThat(context.currentQuestion()).isEqualTo("第二次删除失败怎么办？");
    assertThat(context.currentAnswer()).isEqualTo("通过消息重试补偿");
    assertThat(context.previousTurns()).containsExactly(
        new InterviewTranscriptTurn(1, "你如何处理缓存一致性？", "我使用延迟双删")
    );
  }
}
