package interview.guide.modules.interview.agent.adaptive.memory.episode;

/**
 * Episode enrichment 的不可变状态机。
 */
public record EpisodeEnrichmentState(
    EpisodeEnrichmentStatus status,
    String error
) {

  public EpisodeEnrichmentState {
    if (status == null) {
      throw new NullPointerException("status 不能为空");
    }
    boolean failed = status == EpisodeEnrichmentStatus.FAILED;
    if (failed && (error == null || error.isBlank())) {
      throw new IllegalArgumentException("FAILED 状态必须携带错误");
    }
    if (!failed && error != null) {
      throw new IllegalArgumentException("只有 FAILED 状态必须携带错误");
    }
  }

  public static EpisodeEnrichmentState pending() {
    return new EpisodeEnrichmentState(EpisodeEnrichmentStatus.PENDING, null);
  }

  public EpisodeEnrichmentState claim() {
    require(EpisodeEnrichmentStatus.PENDING, "claim");
    return new EpisodeEnrichmentState(EpisodeEnrichmentStatus.PROCESSING, null);
  }

  public EpisodeEnrichmentState complete() {
    require(EpisodeEnrichmentStatus.PROCESSING, "complete");
    return new EpisodeEnrichmentState(EpisodeEnrichmentStatus.COMPLETED, null);
  }

  public EpisodeEnrichmentState fail(String failure) {
    require(EpisodeEnrichmentStatus.PROCESSING, "fail");
    return new EpisodeEnrichmentState(EpisodeEnrichmentStatus.FAILED, failure);
  }

  public EpisodeEnrichmentState retry() {
    require(EpisodeEnrichmentStatus.FAILED, "retry");
    return pending();
  }

  public EpisodeEnrichmentState recoverStaleProcessing() {
    require(EpisodeEnrichmentStatus.PROCESSING, "recover stale processing");
    return pending();
  }

  public EpisodeEnrichmentState resetAfterAssessmentCorrection() {
    if (status == EpisodeEnrichmentStatus.LEGACY_UNENRICHED) {
      throw new IllegalStateException("历史 Episode 不参与在线再补全");
    }
    return pending();
  }

  private void require(EpisodeEnrichmentStatus expected, String action) {
    if (status != expected) {
      throw new IllegalStateException(
          "Episode enrichment 状态 " + status + " 不能执行 " + action
      );
    }
  }
}
