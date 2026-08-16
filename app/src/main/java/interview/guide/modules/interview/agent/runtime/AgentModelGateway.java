package interview.guide.modules.interview.agent.runtime;

/**
 * 旧版 Agent 模型网关接口，负责单步决策（nextStep）与回答评估（assess）。
 */
public interface AgentModelGateway {

  AgentStep nextStep(InterviewAgentContext context);

  AssessmentResult assess(AssessmentContext context);
}
