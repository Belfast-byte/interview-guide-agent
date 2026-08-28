package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;

public record AdaptiveAnswerFacts(
    MemoryOwner owner,
    String sessionId,
    CandidateAnswer answer
) {}
