package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.List;

public record PracticeDimensionFacts(
    int order,
    String dimension,
    String focus,
    List<PracticeAssessmentFacts> assessments
) {}
