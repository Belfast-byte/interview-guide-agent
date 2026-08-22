package interview.guide.modules.interview.agent.adaptive.codeanalysis.claim;

import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnchor;
import java.util.List;

/**
 * 声明核验值对象。
 */
public record ClaimVerification(
    String claimId,
    String claim,
    ClaimVerificationStatus status,
    List<CodeFact> codeFacts
) {

  public record CodeFact(String finding, CodeAnchor anchor) {}
}
