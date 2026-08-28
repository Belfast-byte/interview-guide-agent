package interview.guide.modules.interview.agent.adaptive.memory.episode;

/** vector_store 返回的曝光题目相似度。 */
public record QuestionSimilarityHit(long exposureId, double similarity) {}
