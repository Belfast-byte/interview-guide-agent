package interview.guide.modules.interview.agent.adaptive.planning;

/**
 * 规划 Agent 接口，负责根据上下文生成面试计划。
 */
public interface PlanningAgent {

  PlanProposal propose(PlanningRequest request, String llmProvider);
}
