package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionPublication;

record QuestionExposureCreation(
    MemoryOwner owner,
    String sessionId,
    long turnId,
    QuestionPublication publication,
    String documentId
) {}
