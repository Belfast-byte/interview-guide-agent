package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/** 为自适应 Agent 的短结构化调用构建显式模型预算。 */
@Component
@RequiredArgsConstructor
public class AdaptiveModelOptionsFactory {

  private final AdaptiveAgentProperties properties;

  public OpenAiChatOptions.Builder planner() {
    return bounded(properties.getPlannerMaxOutputTokens());
  }

  public OpenAiChatOptions.Builder interviewer(List<ToolCallback> callbacks) {
    return bounded(properties.getInterviewerMaxOutputTokens())
        .parallelToolCalls(false)
        .toolCallbacks(callbacks);
  }

  /** 结构化输出场景（深度评估、维度小结、声明抽取）的模型预算。 */
  public OpenAiChatOptions.Builder structured() {
    return bounded(properties.getStructuredMaxOutputTokens());
  }

  private OpenAiChatOptions.Builder bounded(int maxOutputTokens) {
    return OpenAiChatOptions.builder()
        .maxTokens(maxOutputTokens)
        .reasoningEffort(properties.getReasoningEffort());
  }
}
