package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import java.util.List;

/** 每次模型决策看到的事实、当前内存和此前拒绝原因。 */
public record DecisionModelContext(
    AgentContext agentContext,
    List<DecisionObservation> observations
) {

  public DecisionModelContext {
    observations = List.copyOf(observations);
  }
}
