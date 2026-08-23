package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeEnrichmentCompletion;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 在短事务之间执行 LLM 的 Episode enrichment worker。
 */
@Service
@RequiredArgsConstructor
public class EpisodeEnrichmentService {

  private final EpisodeEnrichmentServiceDependencies dependencies;

  public boolean enrich(long episodeId, String llmProvider) {
    if (dependencies.persistence().claim(episodeId).isEmpty()) {
      return false;
    }
    try {
      EpisodeEnrichmentRequest request = dependencies.contextReader().load(episodeId);
      EpisodeEnrichmentProposal proposal = dependencies.generator().generate(
          request,
          llmProvider
      );
      List<ValidatedEpisodeTag> tags = dependencies.tagValidator().validate(
          proposal.tags(),
          request.sourceFacts()
      );
      dependencies.persistence().complete(new EpisodeEnrichmentCompletion(
          episodeId,
          proposal.answerSummary(),
          tags
      ));
      return true;
    } catch (RuntimeException error) {
      dependencies.persistence().fail(episodeId, describe(error));
      throw error;
    }
  }

  private String describe(RuntimeException error) {
    String message = error.getMessage();
    if (message == null || message.isBlank()) {
      return error.getClass().getSimpleName();
    }
    return error.getClass().getSimpleName() + ": " + message;
  }
}
