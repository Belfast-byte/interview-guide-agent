package interview.guide.modules.interview.agent.adaptive.core;

public record AdaptiveInterviewTurn(
    int turnIndex,
    String question,
    String questionReason,
    String answer,
    AgentResponseType responseType,
    String responseContent,
    String decisionReason
) {}
