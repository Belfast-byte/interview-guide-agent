package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.util.List;

public record CodeAnalysisResult(
    ProjectDigest digest,
    List<ClaimVerification> claimVerifications,
    List<ScenarioCard> scenarios,
    long durationMs,
    long tokenCost
) {}
