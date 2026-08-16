package interview.guide.modules.interview.agent.adaptive.assessment;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 深度评估 Agent，按深度量规对候选人回答进行评级并提取证据。
 */
@Service
@RequiredArgsConstructor
public class DepthAssessmentAgent {

  private static final int MAX_RATIONALE_LENGTH = 500;

  private final AssessmentProposalGenerator generator;

  public AssessmentDecision assess(
      AssessmentRequest request,
      String llmProvider
  ) {
    AssessmentProposal proposal = generator.generate(request, llmProvider);
    validate(proposal);
    return new AssessmentDecision(
        request.sessionId(),
        request.turnIndex(),
        proposal.depthLevel(),
        proposal.confidence(),
        proposal.rationaleSummary().trim(),
        proposal.recommendSwitchQuestion(),
        proposal.evidenceQuotes()
    );
  }

  private void validate(AssessmentProposal proposal) {
    if (proposal == null
        || proposal.depthLevel() == null
        || !Double.isFinite(proposal.confidence())
        || proposal.confidence() < 0
        || proposal.confidence() > 1
        || proposal.rationaleSummary() == null
        || proposal.rationaleSummary().isBlank()
        || proposal.rationaleSummary().length() > MAX_RATIONALE_LENGTH
        || proposal.evidenceQuotes() == null
        || proposal.evidenceQuotes().isEmpty()
        || proposal.evidenceQuotes().stream().anyMatch(
            quote -> quote == null || quote.isBlank()
        )) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "回答深度评估结果不完整");
    }
  }
}
