package interview.guide.modules.interview.agent.adaptive.core;

public record AdaptiveInterviewTurn(
    int turnIndex,
    String question,
    String answer,
    AgentResponseType responseType,
    String responseContent,
    String decisionReason
) {}
