package interview.guide.modules.interview.agent.adaptive.memory;

public interface DimensionBriefGenerator {

  DimensionBriefProposal generate(DimensionBriefRequest request, String llmProvider);
}
