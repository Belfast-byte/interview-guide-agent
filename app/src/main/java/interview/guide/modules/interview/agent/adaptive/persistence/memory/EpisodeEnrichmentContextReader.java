package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentContextSource;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentRequest;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEvidenceFact;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeProbeGapFact;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeToolResultFact;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnEntity;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在只读短事务中组装 Episode enrichment 的权威输入。
 */
@Component
@RequiredArgsConstructor
public class EpisodeEnrichmentContextReader implements EpisodeEnrichmentContextSource {

  private final EpisodeEnrichmentRepositories repositories;

  @Transactional(readOnly = true)
  @Override
  public EpisodeEnrichmentRequest load(long episodeId) {
    var episode = repositories.episodes().findById(episodeId)
        .orElseThrow(() -> notFound("EpisodeFact 不存在"))
        .toDomain();
    AdaptiveAgentTurnEntity turn = repositories.turns().findBySessionIdAndTurnIndex(
        episode.sessionId(),
        episode.turnIndex()
    ).orElseThrow(() -> notFound("Episode 对应轮次不存在"));
    AdaptiveAgentAssessmentEntity assessment = repositories.assessments()
        .findById(episode.assessmentId())
        .orElseThrow(() -> notFound("Episode 对应 Assessment 不存在"));
    return new EpisodeEnrichmentRequest(
        episode.id(),
        episode.sessionId(),
        episode.turnIndex(),
        episode.topic(),
        turn.question(),
        turn.answer(),
        assessment.depthLevel(),
        assessment.rationaleSummary(),
        repositories.evidences().findByAssessmentIdOrderById(assessment.id()).stream()
            .map(evidence -> new EpisodeEvidenceFact(
                evidence.id(),
                evidence.evidenceType(),
                evidence.quoteText(),
                evidence.codeAnchor()
            ))
            .toList(),
        loadGaps(turn, assessment),
        loadToolResults(turn, episode.sessionId())
    );
  }

  private List<EpisodeProbeGapFact> loadGaps(
      AdaptiveAgentTurnEntity turn,
      AdaptiveAgentAssessmentEntity assessment
  ) {
    List<EpisodeProbeGapFact> current = gapFacts(assessment.id());
    if (turn.triggerType() != TurnTriggerType.ASSESSMENT_GAP) {
      return current;
    }
    AdaptiveAgentAssessmentEntity source = repositories.assessments()
        .findById(turn.sourceAssessmentId())
        .orElseThrow(() -> notFound("追问来源 Assessment 不存在"));
    if (!source.sessionId().equals(assessment.sessionId())) {
      throw notFound("追问来源 Assessment 不属于当前 session");
    }
    AssessmentProbeGapEntity triggered = repositories.gaps()
        .findById(turn.sourceProbeGapId())
        .orElseThrow(() -> notFound("追问来源 ProbeGap 不存在"));
    if (triggered.assessmentId() != source.id()) {
      throw notFound("追问来源 ProbeGap 不属于来源 Assessment");
    }
    return mergeGaps(current, List.of(toGapFact(triggered)));
  }

  private List<EpisodeProbeGapFact> gapFacts(long assessmentId) {
    return repositories.gaps()
        .findByAssessmentIdOrderByGapOrderAscIdAsc(assessmentId)
        .stream()
        .map(this::toGapFact)
        .toList();
  }

  private List<EpisodeProbeGapFact> mergeGaps(
      List<EpisodeProbeGapFact> current,
      List<EpisodeProbeGapFact> triggered
  ) {
    LinkedHashMap<Long, EpisodeProbeGapFact> unique = new LinkedHashMap<>();
    current.forEach(fact -> unique.putIfAbsent(fact.id(), fact));
    triggered.forEach(fact -> unique.putIfAbsent(fact.id(), fact));
    return List.copyOf(unique.values());
  }

  private List<EpisodeToolResultFact> loadToolResults(
      AdaptiveAgentTurnEntity turn,
      String sessionId
  ) {
    List<EpisodeToolResultFact> current = repositories.toolResults()
        .findEpisodeFactsBySessionIdAndTurnIndex(sessionId, turn.turnIndex());
    if (turn.triggerType() != TurnTriggerType.TOOL_RESULT) {
      return current;
    }
    EpisodeToolResultFact triggered = repositories.toolResults()
        .findEpisodeFactByIdAndSessionId(turn.sourceToolResultEventId(), sessionId)
        .orElseThrow(() -> notFound("追问来源 ToolResult 不属于当前 session"));
    return mergeToolResults(current, triggered);
  }

  private List<EpisodeToolResultFact> mergeToolResults(
      List<EpisodeToolResultFact> current,
      EpisodeToolResultFact triggered
  ) {
    LinkedHashMap<Long, EpisodeToolResultFact> unique = new LinkedHashMap<>();
    current.forEach(fact -> unique.putIfAbsent(fact.id(), fact));
    unique.putIfAbsent(triggered.id(), triggered);
    return List.copyOf(unique.values());
  }

  private EpisodeProbeGapFact toGapFact(AssessmentProbeGapEntity entity) {
    ProbeGap gap = entity.toDomain();
    return new EpisodeProbeGapFact(entity.id(), gap.anchor(), gap.missingPoint());
  }

  private BusinessException notFound(String message) {
    return new BusinessException(ErrorCode.NOT_FOUND, message);
  }
}
