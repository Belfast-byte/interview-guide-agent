package interview.guide.modules.interview.agent.adaptive.assessment.practice;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
/**
 * 练习评估事实。
 */
public record PracticeAssessmentFacts(
    int turnIndex,
    DepthLevel depthLevel
) {}
