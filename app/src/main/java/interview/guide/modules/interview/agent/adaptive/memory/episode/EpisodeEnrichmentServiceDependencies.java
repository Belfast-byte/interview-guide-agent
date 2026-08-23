package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeEnrichmentContextReader;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeEnrichmentPersistenceService;
import org.springframework.stereotype.Component;

/**
 * Episode enrichment 编排依赖组。
 */
@Component
public record EpisodeEnrichmentServiceDependencies(
    EpisodeEnrichmentPersistenceService persistence,
    EpisodeEnrichmentContextReader contextReader,
    EpisodeEnrichmentGenerator generator,
    EpisodeTagValidator tagValidator
) {}
