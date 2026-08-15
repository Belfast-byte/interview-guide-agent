package interview.guide.modules.interview.agent.adaptive.algorithm;

public record AlgorithmProblem(
    String id,
    String title,
    String statement,
    AlgorithmDifficulty difficulty,
    String tags,
    String sampleCasesRef,
    String hiddenCasesRef,
    int timeLimitMs,
    int memoryLimitKb
) {}
