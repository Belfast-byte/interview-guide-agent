package interview.guide.modules.interview.agent.adaptive.tool;

/**
 * 题库题目值对象。
 */
public record QuestionBankQuestion(
    String stableId,
    Long id,
    String category,
    String difficulty,
    String question
) {}
