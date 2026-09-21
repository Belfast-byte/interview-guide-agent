package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;

/** 固定事实与原生消息历史；查询结果只进入 history，不再重复投影到 observations。 */
public record DecisionModelContext(
    AgentContext agentContext,
    List<DecisionObservation> observations,
    List<Message> history,
    List<ToolCallback> tools
) {
  public DecisionModelContext {
    observations = List.copyOf(observations);
    history = List.copyOf(history);
    tools = List.copyOf(tools);
  }

  public DecisionModelContext(AgentContext context, List<DecisionObservation> observations) {
    this(context, observations, List.of(), List.of());
  }
}
