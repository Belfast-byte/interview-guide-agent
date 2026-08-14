package interview.guide.modules.interview.agent.adaptive.memory;

public interface CandidateClaimGenerator {

  CandidateClaimsProposal generate(
      CandidateClaimExtractionRequest request,
      String llmProvider
  );
}
