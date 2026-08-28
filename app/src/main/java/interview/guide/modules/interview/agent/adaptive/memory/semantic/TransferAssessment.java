package interview.guide.modules.interview.agent.adaptive.memory.semantic;

public record TransferAssessment(
    TransferStatus status,
    Long confirmedByEpisodeId
) {}
