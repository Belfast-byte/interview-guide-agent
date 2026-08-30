package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeClosureStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFact;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeRecallSource;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EvaluationRecallView;
import interview.guide.modules.interview.agent.adaptive.memory.episode.PracticeDiagnosticView;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionExposure;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionFingerprint;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionSimilarityHit;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionSimilaritySearch;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaEpisodeRecallSource implements EpisodeRecallSource {

  private final AdaptiveAgentSessionRepository sessionRepository;
  private final QuestionExposureRepository exposureRepository;
  private final EpisodeFactRepository episodeRepository;
  private final AdaptiveAgentTurnRepository turnRepository;
  private final AdaptiveAgentAssessmentRepository assessmentRepository;
  private final AdaptiveAgentEvidenceRepository evidenceRepository;
  private final AssessmentProbeGapRepository gapRepository;
  private final QuestionSimilaritySearch similaritySearch;

  @Override
  @Transactional(readOnly = true)
  public List<EvaluationRecallView> evaluation(
      String sessionId,
      TopicKey topic,
      String question
  ) {
    RecallFacts facts = load(sessionId, topic, question);
    return facts.exposures().stream()
        .map(exposure -> evaluationView(exposure, facts))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<PracticeDiagnosticView> practice(
      String sessionId,
      TopicKey topic,
      String question
  ) {
    RecallFacts facts = load(sessionId, topic, question);
    return facts.exposures().stream()
        .map(exposure -> practiceView(exposure, facts))
        .filter(Objects::nonNull)
        .toList();
  }

  private RecallFacts load(String sessionId, TopicKey topic, String question) {
    AdaptiveAgentSessionEntity session = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND, "Agent 面试会话不存在"));
    MemoryOwner owner = new MemoryOwner(session.tenantId(), session.candidateId());
    List<QuestionExposure> exposures = exposures(owner, topic);
    Map<Long, Double> similarities = similaritySearch.search(owner, topic, question).stream()
        .collect(Collectors.toMap(
            QuestionSimilarityHit::exposureId,
            QuestionSimilarityHit::similarity,
            Math::max
        ));
    return facts(exposures, similarities);
  }

  private RecallFacts facts(
      List<QuestionExposure> exposures,
      Map<Long, Double> similarities
  ) {
    List<Long> turnIds = exposures.stream().map(QuestionExposure::turnId).toList();
    Map<Long, EpisodeFact> episodes = episodeRepository.findByTurnIdIn(turnIds).stream()
        .map(EpisodeFactEntity::toDomain)
        .collect(Collectors.toMap(EpisodeFact::turnId, Function.identity()));
    List<Long> assessmentIds = episodes.values().stream()
        .map(EpisodeFact::assessmentId).toList();
    return new RecallFacts(
        exposures,
        similarities,
        episodes,
        turns(turnIds),
        assessments(assessmentIds),
        evidences(assessmentIds),
        gaps(assessmentIds)
    );
  }

  private List<QuestionExposure> exposures(MemoryOwner owner, TopicKey topic) {
    return exposureRepository.findByOwnerAndTopic(owner, topic).stream()
        .map(QuestionExposureEntity::toDomain)
        .toList();
  }

  private EvaluationRecallView evaluationView(
      QuestionExposure exposure,
      RecallFacts facts
  ) {
    EpisodeFact episode = facts.episodes().get(exposure.turnId());
    return new EvaluationRecallView(
        exposure.exposureId(),
        episode == null ? null : episode.id(),
        exposure.questionText(),
        exposure.identity().scenarioFingerprint(),
        exposure.identity().topic(),
        exposure.identity().evidenceObjective(),
        exposure.identity().probeDepth(),
        exposure.identity().difficulty(),
        similarity(exposure, facts),
        revalidationNeed(episode, facts)
    );
  }

  private String revalidationNeed(EpisodeFact episode, RecallFacts facts) {
    if (episode == null
        || episode.sessionMode() != SessionMode.EVALUATION
        || episode.closureStatus() == EpisodeClosureStatus.RESOLVED) {
      return null;
    }
    List<ProbeGap> gaps = facts.gaps().getOrDefault(episode.assessmentId(), List.of());
    return gaps.isEmpty()
        ? "该知识点尚未形成闭环证据"
        : gaps.stream().map(ProbeGap::missingPoint).collect(Collectors.joining("；"));
  }

  private PracticeDiagnosticView practiceView(
      QuestionExposure exposure,
      RecallFacts facts
  ) {
    EpisodeFact episode = facts.episodes().get(exposure.turnId());
    if (episode == null) {
      return null;
    }
    AdaptiveAgentAssessmentEntity assessment = facts.assessments()
        .get(episode.assessmentId());
    AdaptiveAgentTurnEntity turn = facts.turns().get(exposure.turnId());
    return new PracticeDiagnosticView(
        exposure.exposureId(),
        episode.id(),
        exposure.identity().topic(),
        exposure.questionText(),
        turn.toDomain().answer(),
        assessment.depthLevel(),
        assessment.confidence(),
        facts.evidences().getOrDefault(assessment.id(), List.of()),
        facts.gaps().getOrDefault(assessment.id(), List.of()),
        episode.assistanceLevel(),
        episode.closureStatus(),
        similarity(exposure, facts)
    );
  }

  private double similarity(QuestionExposure exposure, RecallFacts facts) {
    return facts.similarities().getOrDefault(exposure.exposureId(), 0.0);
  }

  private Map<Long, AdaptiveAgentTurnEntity> turns(List<Long> ids) {
    return turnRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(AdaptiveAgentTurnEntity::id, Function.identity()));
  }

  private Map<Long, AdaptiveAgentAssessmentEntity> assessments(List<Long> ids) {
    return assessmentRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(AdaptiveAgentAssessmentEntity::id, Function.identity()));
  }

  private Map<Long, List<String>> evidences(List<Long> assessmentIds) {
    if (assessmentIds.isEmpty()) {
      return Map.of();
    }
    return evidenceRepository.findByAssessmentIds(assessmentIds).stream()
        .collect(Collectors.groupingBy(
            evidence -> evidence.assessment().id(),
            Collectors.mapping(this::evidenceText, Collectors.toList())
        ));
  }

  private String evidenceText(AdaptiveAgentEvidenceEntity evidence) {
    if (evidence.quoteText() != null) {
      return evidence.evidenceType() + ": " + evidence.quoteText();
    }
    if (evidence.sandboxExecutionId() != null) {
      return evidence.evidenceType() + ": " + evidence.sandboxExecutionId();
    }
    return evidence.evidenceType() + ": " + evidence.codeAnchor();
  }

  private Map<Long, List<ProbeGap>> gaps(List<Long> assessmentIds) {
    if (assessmentIds.isEmpty()) {
      return Map.of();
    }
    return gapRepository.findByAssessmentIds(assessmentIds).stream()
        .collect(Collectors.groupingBy(
            AssessmentProbeGapEntity::assessmentId,
            Collectors.mapping(AssessmentProbeGapEntity::toDomain, Collectors.toList())
        ));
  }

  private record RecallFacts(
      List<QuestionExposure> exposures,
      Map<Long, Double> similarities,
      Map<Long, EpisodeFact> episodes,
      Map<Long, AdaptiveAgentTurnEntity> turns,
      Map<Long, AdaptiveAgentAssessmentEntity> assessments,
      Map<Long, List<String>> evidences,
      Map<Long, List<ProbeGap>> gaps
  ) {}
}
