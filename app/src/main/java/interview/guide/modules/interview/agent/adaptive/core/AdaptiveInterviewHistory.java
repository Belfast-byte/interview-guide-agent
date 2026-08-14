package interview.guide.modules.interview.agent.adaptive.core;

import java.util.List;

public record AdaptiveInterviewHistory(
    AdaptiveInterviewSession session,
    String candidateId,
    String jd,
    String resume,
    String llmProvider,
    List<AdaptiveInterviewTurn> turns
) {}
