package interview.guide.modules.interview.agent.adaptive.assessment.practice;

import java.util.List;

/**
 * 练习维度事实。
 */
public record PracticeDimensionFacts(
    int order,
    String dimension,
    String focus,
    List<PracticeAssessmentFacts> assessments
) {}
