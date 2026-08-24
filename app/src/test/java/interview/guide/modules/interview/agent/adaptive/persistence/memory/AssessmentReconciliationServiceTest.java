package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.AnswerHabit;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentRequested;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagSource;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagSourceType;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagValue;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AssessmentRevision;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityCounter;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileRevisionReason;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileSnapshotCreation;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
    EpisodeFactPersistence.class,
    JdbcAbilityCounterIncrementStore.class,
    AbilityProfileSnapshotService.class,
    EpisodeAssessmentCorrectionPersistence.class,
    AssessmentReconciliationDependencies.class,
    AssessmentReconciliationService.class
})
@RecordApplicationEvents
class AssessmentReconciliationServiceTest {

  private static final String SESSION_ID = "session-revision";
  private static final String CANDIDATE_ID = "candidate-1";
  private static final TopicKey TOPIC = new TopicKey("java-backend", "REDIS");

  @Autowired
  private EpisodeFactPersistence episodePersistence;

  @Autowired
  private AssessmentReconciliationService reconciliationService;

  @Autowired
  private AdaptiveAgentAssessmentRepository assessmentRepository;

  @Autowired
  private AbilityCounterRepository counterRepository;

  @Autowired
  private EpisodeFactRepository episodeRepository;

  @Autowired
  private EpisodeTagRepository tagRepository;

  @Autowired
  private ApplicationEvents applicationEvents;

  @Autowired
  private AdaptiveAgentSessionRepository sessionRepository;

  @Autowired
  private CandidateAbilityProfileRepository profileRepository;

  @Test
  @DisplayName("等级修订原子执行旧等级递减和新等级递增")
  void shouldCompensateLevelCounts() {
    persistEpisode();

    reconciliationService.reconcile(revision(DepthLevel.L2, DepthLevel.L4));

    var counter = counter().toDomain();
    assertThat(counter.l2Count()).isZero();
    assertThat(counter.l4Count()).isEqualTo(1);
  }

  @Test
  @DisplayName("等级未变化时计数保持不变")
  void shouldNotChangeCounterForSameLevel() {
    persistEpisode();

    reconciliationService.reconcile(revision(DepthLevel.L2, DepthLevel.L2));

    assertThat(counter().toDomain().l2Count()).isEqualTo(1);
  }

  @Test
  @DisplayName("旧等级计数不足时明确暴露下溢错误")
  void shouldRejectCounterUnderflow() {
    persistEpisode();
    AbilityCounterEntity counter = counter();
    counter.decrement(DepthLevel.L2);
    counterRepository.saveAndFlush(counter);

    assertThatThrownBy(() -> reconciliationService.reconcile(
        revision(DepthLevel.L2, DepthLevel.L4)
    )).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("下溢");
  }

  @Test
  @DisplayName("修订原子清除旧补全结果并单次登记重补全")
  void shouldResetEnrichmentAndRequestOnce() {
    EpisodeFactEntity episode = persistEpisode();
    episode.claimEnrichment();
    episode.completeEnrichment("旧摘要");
    episodeRepository.saveAndFlush(episode);
    tagRepository.saveAndFlush(new EpisodeTagEntity(
        episode,
        EpisodeTagValue.habit(AnswerHabit.STRUCTURED_REASONING),
        new EpisodeTagSource(EpisodeTagSourceType.ASSESSMENT_EVIDENCE, 7)
    ));
    long assessmentId = episode.toDomain().assessmentId();
    applicationEvents.clear();

    reconciliationService.reconcile(revision(DepthLevel.L2, DepthLevel.L4));

    var corrected = episodeRepository.findById(episode.id()).orElseThrow().toDomain();
    assertThat(corrected.assessmentId()).isEqualTo(assessmentId);
    assertThat(corrected.enrichmentStatus()).isEqualTo(EpisodeEnrichmentStatus.PENDING);
    assertThat(corrected.answerSummary()).isNull();
    assertThat(tagRepository.findByEpisodeIdOrderById(episode.id())).isEmpty();
    assertThat(applicationEvents.stream(EpisodeEnrichmentRequested.class))
        .singleElement()
        .satisfies(event -> {
          assertThat(event.episodeId()).isEqualTo(episode.id());
          assertThat(event.llmProvider()).isEqualTo("provider-a");
        });
  }

  @Test
  @DisplayName("已完成会话等级修订生成 ASSESSMENT_CORRECTED 快照")
  void shouldSnapshotCorrectionForCompletedSession() {
    persistEpisode(AdaptiveSessionStatus.COMPLETED);
    profileRepository.saveAndFlush(profile(AbilityProfileRevisionReason.SESSION_COMPLETED));

    reconciliationService.reconcile(revision(DepthLevel.L2, DepthLevel.L4));

    assertThat(profileRepository
        .findByTenantIdIsNullAndCandidateIdOrderByCreatedAtAscIdAsc(CANDIDATE_ID))
        .hasSize(2)
        .satisfiesExactly(
            profile -> assertThat(profile.toDomain().current()).isFalse(),
            profile -> {
              assertThat(profile.toDomain().current()).isTrue();
              assertThat(profile.toDomain().revisionReason())
                  .isEqualTo(AbilityProfileRevisionReason.ASSESSMENT_CORRECTED);
              assertThat(profile.toDomain().counter())
                  .isEqualTo(new AbilityCounter(0, 0, 0, 0, 1));
            }
        );
  }

  @Test
  @DisplayName("进行中会话等级修订不生成 Profile")
  void shouldNotSnapshotCorrectionForActiveSession() {
    persistEpisode(AdaptiveSessionStatus.IN_PROGRESS);

    reconciliationService.reconcile(revision(DepthLevel.L2, DepthLevel.L4));

    assertThat(profileRepository.count()).isZero();
  }

  private EpisodeFactEntity persistEpisode() {
    return persistEpisode(AdaptiveSessionStatus.IN_PROGRESS);
  }

  private EpisodeFactEntity persistEpisode(AdaptiveSessionStatus status) {
    AdaptiveAgentAssessmentEntity assessment = assessmentRepository.saveAndFlush(
        new AdaptiveAgentAssessmentEntity(0, assessment())
    );
    AdaptiveAgentSessionEntity session = sessionRepository.saveAndFlush(session(status));
    return episodePersistence.create(session, assessment, dimension());
  }

  private AbilityCounterEntity counter() {
    return counterRepository.findCandidateCounter(CANDIDATE_ID, TOPIC).orElseThrow();
  }

  private AssessmentRevision revision(DepthLevel oldLevel, DepthLevel newLevel) {
    return new AssessmentRevision(SESSION_ID, 1, oldLevel, newLevel, "provider-a");
  }

  private AssessmentDecision assessment() {
    return new AssessmentDecision(
        SESSION_ID,
        1,
        DepthLevel.L2,
        0.8,
        "初始评估",
        false,
        List.of()
    );
  }

  private CandidateAbilityProfileEntity profile(AbilityProfileRevisionReason reason) {
    return new CandidateAbilityProfileEntity(new AbilityProfileSnapshotCreation(
        new MemoryOwner(null, CANDIDATE_ID),
        TOPIC,
        new AbilityCounter(0, 0, 1, 0, 0),
        SESSION_ID,
        reason
    ));
  }

  private AdaptiveAgentSessionEntity session(AdaptiveSessionStatus status) {
    return new AdaptiveAgentSessionEntity(
        new AdaptiveInterviewSession(
            SESSION_ID,
            AdaptiveInterviewSession.RUNTIME_VERSION,
            status,
            1,
            2
        ),
        new AdaptiveSessionCreation(
            null,
            SESSION_ID,
            CANDIDATE_ID,
            "JD",
            "Resume",
            null,
            null,
            null
        )
    );
  }

  private PlannedDimension dimension() {
    return InterviewPlan.decide(SESSION_ID, new PlanProposal(List.of(
        new DimensionProposal(
            "专业基础",
            "缓存一致性",
            TOPIC.focusId(),
            2,
            List.of(),
            TOPIC.skillId()
        )
    ))).dimensions().getFirst();
  }
}
