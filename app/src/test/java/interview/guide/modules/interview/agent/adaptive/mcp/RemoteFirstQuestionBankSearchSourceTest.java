package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankQuestion;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankSearchSource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteFirstQuestionBankSearchSourceTest {

  @Mock
  private McpQuestionBankGateway remote;

  @Mock
  private QuestionBankSearchSource local;

  @Mock
  private AdaptiveAgentTelemetry telemetry;

  private RemoteFirstQuestionBankSearchSource source;

  @BeforeEach
  void setUp() {
    source = new RemoteFirstQuestionBankSearchSource(remote, local, telemetry);
  }

  @Test
  @DisplayName("远端题库成功时不读取本地题库")
  void shouldPreferRemoteQuestions() {
    List<QuestionBankQuestion> questions = List.of(question(1));
    when(remote.search("Redis", "mid")).thenReturn(questions);

    assertThat(source.search("Redis", "mid")).isSameAs(questions);
    verify(local, never()).search("Redis", "mid");
  }

  @Test
  @DisplayName("远端题库故障时显式记录原因并降级到本地题库")
  void shouldFallbackToLocalQuestions() {
    List<QuestionBankQuestion> questions = List.of(question(2));
    when(remote.search("Redis", null)).thenThrow(new McpQuestionBankException(
        McpQuestionBankFailureReason.TIMEOUT,
        "远端超时"
    ));
    when(local.search("Redis", null)).thenReturn(questions);

    assertThat(source.search("Redis", null)).isSameAs(questions);
    verify(telemetry).mcpQuestionBankFallback(McpQuestionBankFailureReason.TIMEOUT);
  }

  private QuestionBankQuestion question(long id) {
    return new QuestionBankQuestion(
        "question:" + id,
        id,
        "Redis",
        "mid",
        "问题？"
    );
  }
}
