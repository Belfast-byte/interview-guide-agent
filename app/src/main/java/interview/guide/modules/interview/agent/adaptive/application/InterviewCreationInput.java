package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.session.InterviewSessionSettings;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;

record InterviewCreationInput(
    String tenantId,
    String candidateId,
    String jd,
    String resume,
    String llmProviderId,
    String llmProviderNameSnapshot,
    String llmModelSnapshot,
    InterviewSessionSettings settings
) {

  AdaptiveSessionCreation toSessionCreation(String sessionId) {
    return new AdaptiveSessionCreation(
        tenantId,
        sessionId,
        candidateId,
        jd,
        resume,
        llmProviderId,
        llmProviderNameSnapshot,
        llmModelSnapshot,
        settings
    );
  }
}
