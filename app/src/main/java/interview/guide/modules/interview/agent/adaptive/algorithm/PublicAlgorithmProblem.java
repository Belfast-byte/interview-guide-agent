package interview.guide.modules.interview.agent.adaptive.algorithm;

public record PublicAlgorithmProblem(
    String id,
    String title,
    String statement,
    AlgorithmDifficulty difficulty,
    String tags,
    String sampleCases
) {}
