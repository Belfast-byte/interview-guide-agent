package interview.guide.modules.interview.agent.adaptive.memory.episode;

import org.springframework.stereotype.Component;

/**
 * Episode enrichment 编排依赖组。
 */
@Component
public record EpisodeEnrichmentServiceDependencies(
    EpisodeEnrichmentStore store,
    EpisodeEnrichmentContextSource contextReader,
    EpisodeEnrichmentGenerator generator,
    EpisodeTagValidator tagValidator
) {}
