package interview.guide.modules.interview.agent.adaptive.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.NextTurnProvenanceDraft;
import interview.guide.modules.interview.agent.adaptive.memory.episode.AnswerHabit;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagSource;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagSourceType;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagValue;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityCounter;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileRevisionReason;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileSnapshot;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AbilityCounterRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AbilityProfileSnapshotService;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AssessmentReconciliationDependencies;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AssessmentReconciliationService;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateAbilityProfileRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeAssessmentCorrectionPersistence;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactPersistence;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeTagEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeTagRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.JdbcAbilityCounterIncrementStore;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
    AdaptiveInterviewPersistenceService.class,
    AbilityProfileSnapshotService.class,
    EpisodeFactPersistence.class,
    JdbcAbilityCounterIncrementStore.class,
    EpisodeAssessmentCorrectionPersistence.class,
    AssessmentReconciliationDependencies.class,
    AssessmentReconciliationService.class
})
@DisplayName("三层记忆发布不变量")
class ThreeLayerMemoryInvariantIntegrationTest {

  private static final String SESSION_ID = "session-invariant";
  private static final String CANDIDATE_ID = "candidate-invariant";
  private static final TopicKey TOPIC = new TopicKey("java-backend", "REDIS");

  @Autowired private AdaptiveInterviewPersistenceService service;
  @Autowired private AdaptiveAgentAssessmentRepository assessmentRepository;
  @Autowired private AdaptiveAgentEvidenceRepository evidenceRepository;
  @Autowired private EpisodeFactRepository episodeRepository;
  @Autowired private AbilityCounterRepository counterRepository;
  @Autowired private CandidateAbilityProfileRepository profileRepository;
  @Autowired private EpisodeTagRepository tagRepository;
  @Autowired private EntityManager entityManager;

  @Test
  @DisplayName("每个已答轮次唯一追溯 Assessment 且 Counter 等于等级分布，重放不重复")
  void shouldKeepAnsweredTurnCounterAndReplayInvariants() {
    createInterview();
    answer(1, DepthLevel.L1, RespondAction.ask("继续？", "继续验证"));
    answer(2, DepthLevel.L3, RespondAction.finish("完成", "规划完成"));

    List<AdaptiveAgentAssessmentEntity> assessments = assessments();
    assertThat(assessments).hasSize(2);
    assertThat(episodeRepository.countBySessionId(SESSION_ID)).isEqualTo(2);
    assessments.forEach(this::assertEpisodeReferences);
    assertThat(counter()).isEqualTo(distribution(assessments));
    assertThat(counter()).isEqualTo(new AbilityCounter(0, 1, 0, 1, 0));
    assertThat(currentProfile().counter()).isEqualTo(counter());

    assertThatThrownBy(() -> answer(
        2,
        DepthLevel.L3,
        RespondAction.finish("完成", "重放")
    )).isInstanceOf(BusinessException.class);
    assertThat(episodeRepository.countBySessionId(SESSION_ID)).isEqualTo(2);
    assertThat(counter()).isEqualTo(new AbilityCounter(0, 1, 0, 1, 0));
    assertThat(profileRepository.count()).isOne();
  }

  @Test
  @DisplayName("Assessment 修正后 Episode Counter Profile 与标签原子收敛到新等级")
  void shouldKeepCorrectionInvariantAcrossAllMemoryLayers() {
    createInterview();
    answer(1, DepthLevel.L1, RespondAction.ask("继续？", "继续验证"));
    answer(2, DepthLevel.L2, RespondAction.finish("完成", "规划完成"));
    EpisodeFactEntity episode = completedEpisode(2);
    long assessmentId = episode.toDomain().assessmentId();

    service.replaceAssessment(
        SESSION_ID,
        2,
        assessment(2, DepthLevel.L4),
        List.of()
    );
    entityManager.flush();
    entityManager.clear();

    assertThat(assessmentRepository.findById(assessmentId).orElseThrow().depthLevel())
        .isEqualTo(DepthLevel.L4);
    assertThat(evidenceRepository.findByAssessmentIdOrderById(assessmentId)).isEmpty();
    assertCorrectedEpisode(assessmentId);
    assertThat(counter()).isEqualTo(distribution(assessments()));
    assertThat(counter()).isEqualTo(new AbilityCounter(0, 1, 0, 0, 1));
    assertCorrectedProfiles();
  }

  private void assertEpisodeReferences(AdaptiveAgentAssessmentEntity assessment) {
    EpisodeFactEntity episode = episodeRepository.findBySessionIdAndTurnIndex(
        SESSION_ID,
        assessment.turnIndex()
    ).orElseThrow();
    assertThat(episode.toDomain().assessmentId()).isEqualTo(assessment.id());
  }

  private EpisodeFactEntity completedEpisode(int turnIndex) {
    EpisodeFactEntity episode = episodeRepository.findBySessionIdAndTurnIndex(
        SESSION_ID,
        turnIndex
    ).orElseThrow();
    var evidences = evidenceRepository.findByAssessmentIdOrderById(
        episode.toDomain().assessmentId()
    );
    assertThat(evidences).hasSize(1);
    long evidenceId = evidences.getFirst().id();
    episode.claimEnrichment();
    episode.completeEnrichment("旧摘要");
    episodeRepository.saveAndFlush(episode);
    tagRepository.saveAndFlush(new EpisodeTagEntity(
        episode,
        EpisodeTagValue.habit(AnswerHabit.STRUCTURED_REASONING),
        new EpisodeTagSource(
            EpisodeTagSourceType.ASSESSMENT_EVIDENCE,
            evidenceId
        )
    ));
    assertThat(tagRepository.findByEpisodeIdOrderById(episode.id()))
        .singleElement()
        .satisfies(tag -> assertThat(tag.toDomain().source().sourceId())
            .isEqualTo(evidenceId));
    return episode;
  }

  private void assertCorrectedEpisode(long assessmentId) {
    EpisodeFactEntity corrected = episodeRepository.findBySessionIdAndTurnIndex(
        SESSION_ID,
        2
    ).orElseThrow();
    assertThat(corrected.toDomain().assessmentId()).isEqualTo(assessmentId);
    assertThat(corrected.toDomain().enrichmentStatus())
        .isEqualTo(EpisodeEnrichmentStatus.PENDING);
    assertThat(corrected.toDomain().answerSummary()).isNull();
    assertThat(tagRepository.findByEpisodeIdOrderById(corrected.id())).isEmpty();
  }

  private void assertCorrectedProfiles() {
    assertThat(profileRepository
        .findByTenantIdIsNullAndCandidateIdOrderByCreatedAtAscIdAsc(CANDIDATE_ID))
        .hasSize(2)
        .satisfiesExactly(
            profile -> assertThat(profile.toDomain().current()).isFalse(),
            profile -> {
              assertThat(profile.toDomain().current()).isTrue();
              assertThat(profile.toDomain().revisionReason())
                  .isEqualTo(AbilityProfileRevisionReason.ASSESSMENT_CORRECTED);
              assertThat(profile.toDomain().counter()).isEqualTo(counter());
            }
        );
  }

  private void createInterview() {
    service.createSkeleton(new AdaptiveSessionCreation(
        null,
        SESSION_ID,
        CANDIDATE_ID,
        "JD",
        "Resume",
        null,
        "provider-a",
        null
    ));
    service.completeCreation(
        SESSION_ID,
        plan(),
        RespondAction.ask("第一题？", "验证基础"),
        List.of()
    );
  }

  private void answer(int turnIndex, DepthLevel level, RespondAction action) {
    service.recordDecision(new AdaptiveDecisionPersistenceInput(
        new MemoryOwner(null, CANDIDATE_ID),
        SESSION_ID,
        new CandidateAnswer(turnIndex, "回答-" + turnIndex),
        action,
        List.of(),
        null,
        List.of(),
        assessment(turnIndex, level),
        List.of(new ValidatedAssessmentEvidence(
            EvidenceType.QUOTE,
            "回答-" + turnIndex,
            null
        )),
        List.of(),
        NextTurnProvenanceDraft.planned()
    ));
  }

  private AssessmentDecision assessment(int turnIndex, DepthLevel level) {
    return new AssessmentDecision(
        SESSION_ID,
        turnIndex,
        level,
        0.8,
        "已裁决",
        false,
        List.of()
    );
  }

  private List<AdaptiveAgentAssessmentEntity> assessments() {
    return assessmentRepository.findBySessionIdOrderByDimensionOrderAscTurnIndexAsc(
        SESSION_ID
    );
  }

  private AbilityCounter distribution(List<AdaptiveAgentAssessmentEntity> assessments) {
    AbilityCounter result = AbilityCounter.empty();
    for (AdaptiveAgentAssessmentEntity assessment : assessments) {
      result = result.increment(assessment.depthLevel());
    }
    return result;
  }

  private AbilityCounter counter() {
    return counterRepository.findCandidateCounter(CANDIDATE_ID, TOPIC)
        .orElseThrow()
        .toDomain();
  }

  private AbilityProfileSnapshot currentProfile() {
    return profileRepository.findCurrentCandidateProfile(CANDIDATE_ID, TOPIC)
        .orElseThrow()
        .toDomain();
  }

  private InterviewPlan plan() {
    return InterviewPlan.decide(SESSION_ID, new PlanProposal(List.of(
        new DimensionProposal(
            "专业基础",
            "缓存一致性",
            TOPIC.focusId(),
            2,
            List.of(),
            TOPIC.skillId()
        )
    )));
  }
}
