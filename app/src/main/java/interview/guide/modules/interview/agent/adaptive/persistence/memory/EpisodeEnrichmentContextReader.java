package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentRequest;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEvidenceFact;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeProbeGapFact;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在只读短事务中组装 Episode enrichment 的权威输入。
 */
@Component
@RequiredArgsConstructor
public class EpisodeEnrichmentContextReader {

  private final EpisodeEnrichmentRepositories repositories;

  @Transactional(readOnly = true)
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
        repositories.gaps().findByAssessmentIdOrderByGapOrderAscIdAsc(assessment.id()).stream()
            .map(this::toGapFact)
            .toList(),
        repositories.toolResults().findEpisodeFactsBySessionIdAndTurnIndex(
            episode.sessionId(),
            episode.turnIndex()
        )
    );
  }

  private EpisodeProbeGapFact toGapFact(AssessmentProbeGapEntity entity) {
    ProbeGap gap = entity.toDomain();
    return new EpisodeProbeGapFact(entity.id(), gap.anchor(), gap.missingPoint());
  }

  private BusinessException notFound(String message) {
    return new BusinessException(ErrorCode.NOT_FOUND, message);
  }
}
