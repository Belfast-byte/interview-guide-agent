package interview.guide.modules.interview.agent.runtime;

import java.util.UUID;

/**
 * 旧版 Agent 模型网关接口，负责单步决策（nextStep）与回答评估（assess）。
 */
public interface AgentModelGateway {

  AgentStep nextStep(UUID candidateId, InterviewAgentContext context);

  AssessmentResult assess(UUID candidateId, AssessmentContext context);
}
