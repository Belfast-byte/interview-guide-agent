package interview.guide.modules.interview.agent.adaptive.assessment.depth;

import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;

public record GapResolution(long gapId, SourceQuote evidenceQuote, String reason) {}
