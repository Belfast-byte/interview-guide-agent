package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

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
