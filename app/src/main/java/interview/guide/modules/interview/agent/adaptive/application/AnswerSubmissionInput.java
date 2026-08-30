package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;

record AnswerSubmissionInput(
    String tenantId,
    String sessionId,
    CandidateAnswer answer,
    AnswerEventSink sink
) {}
