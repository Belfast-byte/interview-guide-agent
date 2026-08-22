package interview.guide.modules.interview.agent.adaptive.memory.brief;

/**
 * 维度简报生成器接口。
 */
public interface DimensionBriefGenerator {

  DimensionBriefProposal generate(DimensionBriefRequest request, String llmProvider);
}
