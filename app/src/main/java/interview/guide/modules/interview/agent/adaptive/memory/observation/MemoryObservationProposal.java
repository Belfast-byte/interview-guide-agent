package interview.guide.modules.interview.agent.adaptive.memory.observation;

/** 模型提供语义建议；来源、独立机会及有效版本由服务端验证。 */
public record MemoryObservationProposal(
    int objectiveIndex, Finding finding, String evidenceQuote,
    Assistance assistance, String assistanceQuote, Long relatedRevisionId,
    Relation relation, boolean sameCapability, boolean differentScenario,
    boolean answerExposed, String rationale
) {
  public enum Finding { CORRECT, INCORRECT, INSUFFICIENT }
  public enum Assistance { NONE, NEUTRAL_PROBE, HINT, EXPLANATION, TOOL_ASSISTED, UNKNOWN }
  public enum Relation { NEW, ELABORATION, CORRECTION, SUPPORT, CONTRADICTION, INCOMPARABLE }
}
