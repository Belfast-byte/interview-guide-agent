package interview.guide.modules.interview.agent.adaptive.persistence.memory;

/**
 * Episode 与权威 Session 联查得到的 enrichment 投递参数。
 */
public interface EpisodeEnrichmentJobProjection {

  long getEpisodeId();

  String getSessionId();

  String getLlmProvider();
}
