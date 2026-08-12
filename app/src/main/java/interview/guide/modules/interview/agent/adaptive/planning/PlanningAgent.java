package interview.guide.modules.interview.agent.adaptive.planning;

public interface PlanningAgent {

  PlanProposal propose(PlanningRequest request, String llmProvider);
}
