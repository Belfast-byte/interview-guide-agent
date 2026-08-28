package interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario;

import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxLanguage;

public record PatchCodeSubmission(
    String sessionId,
    int turnIndex,
    String scenarioId,
    SandboxLanguage language,
    String patch,
    String idempotencyKey
) {}
