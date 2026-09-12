package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.CodeRepairReview;
import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.CodeRepairTaskResponse;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.QuestionType;

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
        sessionTurns.get(fact.getSessionId()))).toList();
  }

  private EpisodeView view(EpisodeProjection fact, List<AssessmentProbeGapEntity> missing,
      Map<Integer, PriorTurnProjection> turns) {
    boolean visible = fact.getCodeTaskTurnIndex() == null || fact.getSessionMode() == SessionMode.PRACTICE
        || fact.getSessionStatus() == AdaptiveSessionStatus.COMPLETED;
    return new EpisodeView(fact.getEpisodeId(), fact.getSessionId(), fact.getTurnIndex(),
        fact.getSessionMode(), fact.getAssessmentId(),
        new TopicKey(fact.getSkillId(), fact.getFocusId()), fact.getQuestion(), fact.getAnswer(),
        fact.getDepthLevel(), fact.getExpectedDepth(), visible ? fact.getRationaleSummary() : null,
        visible ? missing.stream().map(gap -> publicGap(fact, gap, turns)).toList() : List.of(),
        fact.getTriggerType(), priorTurns(fact, turns), fact.getCreatedAt(), fact.getQuestionType(),
        fact.getCodeTaskTurnIndex(), publicTask(fact), fact.getSubmittedCode(),
        visible ? fact.getCodeReview() : null);
  }

  private Gap publicGap(EpisodeProjection fact, AssessmentProbeGapEntity gap, Map<Integer, PriorTurnProjection> turns) {
    Integer closingTurn = gap.closedByTurnIndex();
    boolean visible = closingTurn == null || fact.getSessionMode() == SessionMode.PRACTICE
        || fact.getSessionStatus() == AdaptiveSessionStatus.COMPLETED
        || turns.get(closingTurn).getCodeTaskTurnIndex() == null;
    return new Gap(gap.id(), gap.toDomain().anchor().quote(), gap.toDomain().missingPoint(),
        gap.closedByAssessmentId(), visible ? gap.closureEvidenceQuote() : null,
        visible ? gap.closureSummary() : null, gap.anchorLocator(), visible ? gap.closureEvidenceLocator() : null);
  }

  private CodeRepairTaskResponse publicTask(EpisodeProjection fact) {
    if (fact.getCodeTaskTurnIndex() == null) return null;
    if (fact.getCodeTask() == null) throw new IllegalStateException("Episode 缺少关联原始代码任务");
    return fact.getCodeTask().publicView();
  }

  /** 保留实际前文，避免把追问或提示后的回答解释为无提示作答。 */
  private List<AnswerContext> priorTurns(EpisodeProjection fact, Map<Integer, PriorTurnProjection> turns) {
    var result = new ArrayList<AnswerContext>();
    Integer parent = fact.getParentTurnIndex();
    while (parent != null) {
      var turn = turns.get(parent);
      boolean published = fact.getSessionMode() == SessionMode.PRACTICE && turn.getCodeTaskTurnIndex() != null;
      result.add(new AnswerContext(turn.getTurnIndex(), turn.getQuestion(), turn.getAnswer(),
          turn.getSubmittedCode(), published ? turn.getCodeReview() : null,
          published ? turn.getFeedbackRationale() : null));
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
      LocalDateTime createdAt, QuestionType questionType, Integer codeTaskTurnIndex,
      CodeRepairTaskResponse codeTask, String submittedCode, CodeRepairReview codeReview
  ) {
    public String reference() {
      return "episode:" + episodeId;
    }
  }

  public record Gap(long gapId, String anchor, String missingPoint,
      Long closedByAssessmentId, String closureEvidenceQuote, String closureSummary,
      SourceQuote.Locator anchorLocator, SourceQuote.Locator closureEvidenceLocator) {}

}
