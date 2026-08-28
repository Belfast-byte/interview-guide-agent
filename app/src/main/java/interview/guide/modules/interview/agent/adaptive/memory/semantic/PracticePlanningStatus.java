package interview.guide.modules.interview.agent.adaptive.memory.semantic;

public record PracticePlanningStatus(
    EvaluatedAbility evaluatedAbility,
    PracticeMastery practiceMastery,
    TransferStatus transferStatus
) {}
