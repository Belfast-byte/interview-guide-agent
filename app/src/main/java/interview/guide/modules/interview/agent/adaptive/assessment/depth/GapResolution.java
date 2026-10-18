package interview.guide.modules.interview.agent.adaptive.assessment.depth;

import io.swagger.v3.oas.annotations.media.Schema;
import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;

public record GapResolution(
    long gapId,
    @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) SourceQuote evidenceQuote,
    String reason
) {}
