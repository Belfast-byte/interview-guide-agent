package interview.guide.modules.interview.agent.adaptive.codeanalysis.job;

import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnalysisResult;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.claim.ClaimVerification;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.ProjectDigest;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario.ScenarioCard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * 代码分析结果请求。
 */
public record CodeAnalysisResultRequest(
    @NotNull @Valid ProjectDigest digest,
    @NotNull List<@Valid ClaimVerification> claimVerifications,
    @NotNull List<@Valid ScenarioCard> scenarios,
    @PositiveOrZero long durationMs,
    @PositiveOrZero long tokenCost
) {

  CodeAnalysisResult toDomain() {
    return new CodeAnalysisResult(
        digest,
        claimVerifications,
        scenarios,
        durationMs,
        tokenCost
    );
  }
}
