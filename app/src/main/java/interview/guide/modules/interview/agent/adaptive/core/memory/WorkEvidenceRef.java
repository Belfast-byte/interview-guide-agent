package interview.guide.modules.interview.agent.adaptive.core.memory;

/** WorkState 对已落库证据的轻量引用。 */
public record WorkEvidenceRef(
    String targetId,
    String sourceType,
    String sourceId,
    String quote
) {}
