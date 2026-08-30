package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.PracticeCoachingContext;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeRecallSource;
import interview.guide.modules.interview.agent.adaptive.memory.episode.PracticeDiagnosticView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PracticeCoachingMemoryAssembler {

  private final PracticeMemoryService memoryService;
  private final EpisodeRecallSource episodeRecall;
  private final PracticeMemoryOwnerSource ownerSource;

  public PracticeCoachingContext assemble(PracticeCoachingRequest request) {
    if (request.session().mode() != SessionMode.PRACTICE) {
      return null;
    }
    PracticePlanningTopic semantic = memoryService.planning(
        ownerSource.findOwner(request.session().sessionId()),
        new PracticeScope(List.of(request.topic()))
    ).topics().getFirst();
    List<Map<String, Object>> episodes = episodeRecall.practice(
            request.session().sessionId(), request.topic(), request.query())
        .stream()
        .map(this::episode)
        .toList();
    return new PracticeCoachingContext(semantic(semantic), episodes);
  }

  private Map<String, Object> semantic(PracticePlanningTopic topic) {
    return Map.of(
        "topic", topic.topic(),
        "status", status(topic.status()),
        "stablePatterns", topic.stablePatterns()
    );
  }

  private Map<String, Object> status(PracticePlanningStatus status) {
    Map<String, Object> values = new LinkedHashMap<>();
    if (status.evaluatedAbility() != null) {
      values.put("evaluatedAbility", status.evaluatedAbility());
    }
    if (status.practiceMastery() != null) {
      values.put("practiceMastery", status.practiceMastery());
    }
    if (status.transferStatus() != null) {
      values.put("transferStatus", status.transferStatus());
    }
    return Map.copyOf(values);
  }

  private Map<String, Object> episode(PracticeDiagnosticView source) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("exposureId", source.exposureId());
    values.put("episodeId", source.episodeId());
    values.put("topic", source.topic());
    values.put("question", source.question());
    values.put("answer", source.answer());
    values.put("rating", source.rating());
    values.put("confidence", source.confidence());
    values.put("evidence", source.evidence());
    values.put("gaps", source.gaps());
    values.put("assistanceLevel", source.assistanceLevel());
    values.put("closureStatus", source.closureStatus());
    values.put("similarity", source.similarity());
    return Map.copyOf(values);
  }
}
