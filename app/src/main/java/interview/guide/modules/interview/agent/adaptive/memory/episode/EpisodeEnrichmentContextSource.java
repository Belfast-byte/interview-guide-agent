package interview.guide.modules.interview.agent.adaptive.memory.episode;

/**
 * 从权威存储读取 Episode enrichment 输入的端口。
 */
public interface EpisodeEnrichmentContextSource {

  EpisodeEnrichmentRequest load(long episodeId);
}
