package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;

/** 正式面试可见的中性召回，不包含历史答案、评级、标签或辅助信息。 */
public record EvaluationRecallView(
    long exposureId,
    Long episodeId,
    String question,
    String scenarioFingerprint,
    TopicKey topic,
    String evidenceObjective,
    DepthLevel probeDepth,
    String difficulty,
    double similarity,
    String revalidationNeed
) {}
