package interview.guide.modules.interview.agent.adaptive.memory.claim;

/**
 * 候选人声明生成器接口。
 */
public interface CandidateClaimGenerator {

  CandidateClaimsProposal generate(
      CandidateClaimExtractionRequest request,
      String llmProvider
  );
}
