package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import interview.guide.modules.interview.agent.adaptive.codeanalysis.claim.ClaimVerification;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.ProjectDigest;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario.ScenarioCard;
import java.util.List;

/**
 * 代码分析结果。
 */
public record CodeAnalysisResult(
    ProjectDigest digest,
    List<ClaimVerification> claimVerifications,
    List<ScenarioCard> scenarios,
    long durationMs,
    long tokenCost
) {}
