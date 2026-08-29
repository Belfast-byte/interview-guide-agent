package interview.guide.modules.interview.agent.adaptive.runtime;

/** InterviewAgentLoop 使用的结构化模型边界。 */
@FunctionalInterface
public interface InterviewDecisionModel {

  AgentDecision decide(DecisionModelContext context);
}
