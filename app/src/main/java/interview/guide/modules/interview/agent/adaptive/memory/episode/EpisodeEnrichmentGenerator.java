package interview.guide.modules.interview.agent.adaptive.memory.episode;

public interface EpisodeEnrichmentGenerator {

  EpisodeEnrichmentProposal generate(
      EpisodeEnrichmentRequest request,
      String llmProvider
  );
}
