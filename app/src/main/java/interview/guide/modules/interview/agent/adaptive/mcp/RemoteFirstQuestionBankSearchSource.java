package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankQuestion;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankSearchSource;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 远程优先的题库搜索源，先走 MCP 远程题库再回退本地。
 */
@Primary
@Component
@ConditionalOnProperty(
    prefix = "app.interview.adaptive-agent.mcp.question-bank",
    name = "enabled",
    havingValue = "true"
)
public class RemoteFirstQuestionBankSearchSource implements QuestionBankSearchSource {

  private final McpQuestionBankGateway remote;
  private final QuestionBankSearchSource local;
  private final AdaptiveAgentTelemetry telemetry;

  public RemoteFirstQuestionBankSearchSource(
      McpQuestionBankGateway remote,
      @Qualifier("localQuestionBankSearchSource") QuestionBankSearchSource local,
      AdaptiveAgentTelemetry telemetry
  ) {
    this.remote = remote;
    this.local = local;
    this.telemetry = telemetry;
  }

  @Override
  public List<QuestionBankQuestion> search(String query, String difficulty) {
    try {
      return remote.search(query, difficulty);
    } catch (McpQuestionBankException e) {
      telemetry.mcpQuestionBankFallback(e.reason());
      return local.search(query, difficulty);
    }
  }
}
