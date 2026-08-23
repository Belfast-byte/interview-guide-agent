package interview.guide.modules.interview.agent.adaptive.application;

import java.util.UUID;

/** 候选人发起自适应面试所需的创建参数。 */
public record CandidateInterviewCreationCommand(
    UUID candidateId,
    String jd,
    String resume,
    String requestedProviderId
) {}
