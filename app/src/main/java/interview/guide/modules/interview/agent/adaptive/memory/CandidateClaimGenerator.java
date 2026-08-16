package interview.guide.modules.interview.agent.adaptive.memory;

/**
 * 候选人声明生成器接口。
 */
public interface CandidateClaimGenerator {

  CandidateClaimsProposal generate(
      CandidateClaimExtractionRequest request,
      String llmProvider
  );
}
