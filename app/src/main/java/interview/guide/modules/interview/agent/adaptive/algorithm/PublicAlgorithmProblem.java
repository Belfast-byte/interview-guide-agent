package interview.guide.modules.interview.agent.adaptive.algorithm;

/**
 * 对外暴露的算法题目。
 */
public record PublicAlgorithmProblem(
    String id,
    String title,
    String statement,
    AlgorithmDifficulty difficulty,
    String tags,
    String sampleCases
) {}
