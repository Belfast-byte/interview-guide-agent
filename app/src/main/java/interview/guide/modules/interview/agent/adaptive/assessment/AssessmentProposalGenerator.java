package interview.guide.modules.interview.agent.adaptive.assessment;

public interface AssessmentProposalGenerator {

  AssessmentProposal generate(AssessmentRequest request, String llmProvider);
}
