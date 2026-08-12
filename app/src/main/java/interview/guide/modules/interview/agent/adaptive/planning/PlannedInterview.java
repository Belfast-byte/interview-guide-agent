package interview.guide.modules.interview.agent.adaptive.planning;

import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewHistory;

public record PlannedInterview(
    AdaptiveInterviewHistory history,
    InterviewPlan plan
) {}
