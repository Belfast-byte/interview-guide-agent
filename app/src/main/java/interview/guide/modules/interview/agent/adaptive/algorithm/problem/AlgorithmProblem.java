package interview.guide.modules.interview.agent.adaptive.algorithm.problem;

/**
 * 算法题目值对象。
 */
public record AlgorithmProblem(
    String id,
    String title,
    String statement,
    AlgorithmDifficulty difficulty,
    String tags,
    String sampleCasesRef,
    String hiddenCasesRef,
    int timeLimitMs,
    int memoryLimitKb,
    String variantGroup
) {}
