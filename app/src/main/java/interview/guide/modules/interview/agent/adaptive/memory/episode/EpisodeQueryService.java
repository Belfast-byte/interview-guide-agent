package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn.AnswerContext;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import interview.guide.modules.interview.agent.adaptive.memory.episode.CandidateMemoryEpisodeQueryRepository.EpisodeProjection;
import interview.guide.modules.interview.agent.adaptive.memory.episode.CandidateMemoryEpisodeQueryRepository.PriorTurnProjection;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 原题、回答、评级和 gap 直接来自答题事实，不依赖后台整理。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EpisodeQueryService {
  private final CandidateMemoryEpisodeQueryRepository episodes;
  private final AssessmentProbeGapRepository gaps;

  public Page<EpisodeView> page(MemoryOwner owner, Pageable pageable) {
    var page = episodes.findByOwner(owner, pageable);
    return new PageImpl<>(assemble(page.getContent()), pageable, page.getTotalElements());
  }

  public List<EpisodeView> recent(MemoryOwner owner, TopicKey topic, Pageable pageable) {
    return assemble(episodes.findByTopic(owner, topic, pageable));
  }

  public List<EpisodeView> latest(MemoryOwner owner) {
    return assemble(episodes.findLatestByTopic(owner));
  }

  private List<EpisodeView> assemble(List<EpisodeProjection> facts) {
    if (facts.isEmpty()) {
      return List.of();
    }
    var byAssessment = gaps.findByAssessmentIds(facts.stream()
            .map(EpisodeProjection::getAssessmentId).toList()).stream()
        .collect(Collectors.groupingBy(AssessmentProbeGapEntity::assessmentId));
    var sessionTurns = episodes.findSessionTurns(facts.stream()
            .map(EpisodeProjection::getSessionId).distinct().toList()).stream()
        .collect(Collectors.groupingBy(PriorTurnProjection::getSessionId,
            Collectors.toMap(PriorTurnProjection::getTurnIndex, Function.identity())));
    return facts.stream().map(fact -> view(fact,
        byAssessment.getOrDefault(fact.getAssessmentId(), List.of()),
        priorTurns(fact, sessionTurns.get(fact.getSessionId())))).toList();
  }

  private EpisodeView view(EpisodeProjection fact, List<AssessmentProbeGapEntity> missing,
      List<AnswerContext> priorTurns) {
    return new EpisodeView(fact.getEpisodeId(), fact.getSessionId(), fact.getTurnIndex(),
        fact.getSessionMode(), fact.getAssessmentId(),
        new TopicKey(fact.getSkillId(), fact.getFocusId()), fact.getQuestion(), fact.getAnswer(),
        fact.getDepthLevel(), fact.getExpectedDepth(), fact.getRationaleSummary(),
        missing.stream().map(gap -> new Gap(gap.id(), gap.toDomain().anchor(),
            gap.toDomain().missingPoint(), gap.closedByAssessmentId(),
            gap.closureEvidenceQuote(), gap.closureSummary())).toList(),
        fact.getTriggerType(), priorTurns, fact.getCreatedAt());
  }

  /** 保留实际前文，避免把追问或提示后的回答解释为无提示作答。 */
  private List<AnswerContext> priorTurns(EpisodeProjection fact, Map<Integer, PriorTurnProjection> turns) {
    var result = new ArrayList<AnswerContext>();
    Integer parent = fact.getParentTurnIndex();
    while (parent != null) {
      var turn = turns.get(parent);
      result.add(new AnswerContext(turn.getTurnIndex(), turn.getQuestion(), turn.getAnswer()));
      parent = turn.getParentTurnIndex();
    }
    Collections.reverse(result);
    return List.copyOf(result);
  }

  /** 供页面及模型读取的公共视图，不暴露用户归属或执行状态。 */
  public record EpisodeView(
      long episodeId, String sessionId, int turnIndex, SessionMode sessionMode,
      long assessmentId, TopicKey topic, String question, String answer,
      DepthLevel depthLevel, DepthLevel expectedDepth, String rationaleSummary,
      List<Gap> gaps, TurnTriggerType triggerType, List<AnswerContext> priorTurns,
      LocalDateTime createdAt
  ) {
    public String reference() {
      return "episode:" + episodeId;
    }
  }

  public record Gap(long gapId, String anchor, String missingPoint,
      Long closedByAssessmentId, String closureEvidenceQuote, String closureSummary) {}

}
