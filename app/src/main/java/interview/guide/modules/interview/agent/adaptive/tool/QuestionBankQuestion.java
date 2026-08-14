package interview.guide.modules.interview.agent.adaptive.tool;

public record QuestionBankQuestion(
    String stableId,
    Long id,
    String category,
    String difficulty,
    String question
) {}
