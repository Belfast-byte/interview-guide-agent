package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;

/** 最终事务所需的 Assessor 正式事实与 Agent 最终决定。 */
public record AnswerProgressionDecision(
    AnswerAssessment assessment,
    AgentDecision agentDecision
) {}
