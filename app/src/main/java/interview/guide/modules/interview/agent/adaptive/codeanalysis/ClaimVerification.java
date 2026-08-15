package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.util.List;

public record ClaimVerification(
    String claimId,
    String claim,
    ClaimVerificationStatus status,
    List<CodeFact> codeFacts
) {

  public record CodeFact(String finding, CodeAnchor anchor) {}
}
