package interview.guide.modules.interview.agent.adaptive.memory.episode;

/**
 * Episode 非权威展示信息的异步补全状态。
 */
public enum EpisodeEnrichmentStatus {
  PENDING,
  PROCESSING,
  COMPLETED,
  FAILED,
  LEGACY_UNENRICHED
}
