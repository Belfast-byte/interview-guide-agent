package interview.guide.modules.interview.agent.adaptive.runtime;

/** Java 拒绝模型提案后返回模型的结构化原因。 */
public record DecisionObservation(
    String code,
    String field,
    String message
) {}
