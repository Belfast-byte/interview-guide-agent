package interview.guide.modules.interview.agent.adaptive.core;

import java.util.List;

public record AdaptiveInterviewHistory(
    AdaptiveInterviewSession session,
    List<AdaptiveInterviewTurn> turns
) {}
