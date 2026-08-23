package interview.guide.modules.interview.agent.adaptive.persistence.session;

public record AdaptiveSessionCreation(
    String tenantId,
    String sessionId,
    String candidateId,
    String jd,
    String resume,
    String llmProviderId,
    String llmProviderNameSnapshot,
    String llmModelSnapshot
) {}
