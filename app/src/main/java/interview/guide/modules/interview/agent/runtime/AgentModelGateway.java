package interview.guide.modules.interview.agent.runtime;

public interface AgentModelGateway {

  AgentStep nextStep(InterviewAgentContext context);

  AssessmentResult assess(AssessmentContext context);
}
