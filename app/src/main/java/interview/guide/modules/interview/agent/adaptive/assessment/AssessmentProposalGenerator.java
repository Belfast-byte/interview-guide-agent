package interview.guide.modules.interview.agent.adaptive.assessment;

/**
 * 评估建议生成器接口。
 */
public interface AssessmentProposalGenerator {

  AssessmentProposal generate(AssessmentRequest request, String llmProvider);
}
