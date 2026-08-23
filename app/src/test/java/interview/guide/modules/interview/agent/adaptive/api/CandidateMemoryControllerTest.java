package interview.guide.modules.interview.agent.adaptive.api;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.common.security.AuthenticatedUser;
import interview.guide.modules.auth.domain.UserRole;
import interview.guide.modules.interview.agent.adaptive.application.CandidateMemoryQueryService;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.AnswerHabit;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactCreation;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagSource;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagSourceType;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagValue;
import interview.guide.modules.interview.agent.adaptive.memory.episode.ErrorPattern;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityCounter;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileRevisionReason;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileSnapshotCreation;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateAbilityProfileEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateAbilityProfileRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeTagEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeTagRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(CandidateMemoryQueryService.class)
@DisplayName("候选人长期记忆查询接口")
class CandidateMemoryControllerTest {
  private static final UUID CANDIDATE_ID = UUID.fromString(
      "11111111-1111-1111-1111-111111111111"
  );
  private static final MemoryOwner CANDIDATE = new MemoryOwner(null, CANDIDATE_ID.toString());
  private static final MemoryOwner TENANT_CANDIDATE = new MemoryOwner(
      "tenant-a",
      CANDIDATE_ID.toString()
  );
  private static final MemoryOwner OTHER_CANDIDATE = new MemoryOwner(
      null,
      "22222222-2222-2222-2222-222222222222"
  );
  private static final TopicKey REDIS = new TopicKey("java-backend", "REDIS");
  private static final TopicKey JVM = new TopicKey("java-backend", "JVM");
  private static final LocalDateTime EPISODE_TIME = LocalDateTime.of(2026, 8, 23, 12, 0);

  @Autowired private CandidateMemoryQueryService queryService;
  @Autowired private CandidateAbilityProfileRepository profileRepository;
  @Autowired private AdaptiveAgentSessionRepository sessionRepository;
  @Autowired private AdaptiveAgentAssessmentRepository assessmentRepository;
  @Autowired private EpisodeFactRepository episodeRepository;
  @Autowired private EpisodeTagRepository tagRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  private CandidateMemoryController controller;

  @BeforeEach
  void setUp() {
    controller = new CandidateMemoryController(queryService);
  }
  @Test
  @DisplayName("返回稳定排序的 Profile、等级分布、标签计数和 Episode 链")
  void shouldReturnCandidateMemoryInStableOrder() {
    saveProfile(new ProfileFixture(CANDIDATE, REDIS, "profile-redis", new AbilityCounter(
        0, 1, 1, 1, 0
    )));
    saveProfile(new ProfileFixture(CANDIDATE, JVM, "profile-jvm", new AbilityCounter(
        0, 0, 0, 1, 1
    )));
    saveInvisibleOwners();
    EpisodeFactEntity root = saveEpisode(new EpisodeFixture(
        CANDIDATE, "candidate-chain", 1, null, REDIS, DepthLevel.L2, null
    ));
    EpisodeFactEntity child = saveEpisode(new EpisodeFixture(
        CANDIDATE,
        "candidate-chain",
        2,
        1,
        REDIS,
        DepthLevel.L3,
        root.toDomain().assessmentId()
    ));
    saveTags(root, List.of(EpisodeTagValue.error(ErrorPattern.MISSING_FAILURE_BOUNDARY)));
    saveTags(child, List.of(
        EpisodeTagValue.error(ErrorPattern.MISSING_FAILURE_BOUNDARY),
        EpisodeTagValue.habit(AnswerHabit.STRUCTURED_REASONING)
    ));
    alignEpisodeTimes(root.id(), child.id());
    var result = controller.get(principal(CANDIDATE_ID), 0);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData().topics())
        .extracting(CandidateMemoryResponse.TopicProfileResponse::focusId)
        .containsExactly("JVM", "REDIS");
    assertRedisTopic(result.getData().topics().get(1));
    assertThat(result.getData().episodes().content())
        .extracting(CandidateMemoryResponse.EpisodeResponse::turnIndex)
        .containsExactly(2, 1);
    assertThat(result.getData().episodes().content().get(0).parentTurnIndex()).isEqualTo(1);
    assertThat(result.getData().episodes().totalElements()).isEqualTo(2);
    assertThat(result.getData().episodes().last()).isTrue();
  }

  @Test
  @DisplayName("无长期记忆时返回空集合和空分页")
  void shouldReturnEmptyMemory() {
    UUID unknown = UUID.fromString("33333333-3333-3333-3333-333333333333");
    var response = controller.get(principal(unknown), 0).getData();
    assertThat(response.candidateId()).isEqualTo(unknown.toString());
    assertThat(response.topics()).isEmpty();
    assertThat(response.episodes().content()).isEmpty();
    assertThat(response.episodes().totalElements()).isZero();
  }

  @Test
  @DisplayName("Episode 响应不暴露题答、摘要、评估依据或内部来源")
  void shouldExposeOnlySafeEpisodeFields() {
    assertThat(Arrays.stream(CandidateMemoryResponse.EpisodeResponse.class
        .getRecordComponents()).map(component -> component.getName()))
        .containsExactly(
            "sessionId",
            "turnIndex",
            "parentTurnIndex",
            "skillId",
            "focusId",
            "depthLevel",
            "enrichmentStatus",
            "createdAt"
        );
  }

  private void assertRedisTopic(CandidateMemoryResponse.TopicProfileResponse topic) {
    assertThat(topic.skillId()).isEqualTo("java-backend");
    assertThat(List.of(
        topic.l0Count(), topic.l1Count(), topic.l2Count(), topic.l3Count(), topic.l4Count()
    )).containsExactly(0L, 1L, 1L, 1L, 0L);
    assertThat(topic.tagCounts())
        .extracting(CandidateMemoryResponse.TagCountResponse::tag,
            CandidateMemoryResponse.TagCountResponse::count)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("STRUCTURED_REASONING", 1L),
            org.assertj.core.groups.Tuple.tuple("MISSING_FAILURE_BOUNDARY", 2L)
        );
  }

  private void saveInvisibleOwners() {
    saveProfile(new ProfileFixture(
        TENANT_CANDIDATE,
        REDIS,
        "tenant-profile",
        new AbilityCounter(0, 0, 0, 0, 1)
    ));
    saveProfile(new ProfileFixture(
        OTHER_CANDIDATE,
        REDIS,
        "other-profile",
        new AbilityCounter(1, 0, 0, 0, 0)
    ));
    saveEpisode(new EpisodeFixture(
        TENANT_CANDIDATE, "tenant-session", 1, null, REDIS, DepthLevel.L4, null
    ));
    saveEpisode(new EpisodeFixture(
        OTHER_CANDIDATE, "other-session", 1, null, REDIS, DepthLevel.L0, null
    ));
  }

  private void saveProfile(ProfileFixture fixture) {
    profileRepository.saveAndFlush(new CandidateAbilityProfileEntity(
        new AbilityProfileSnapshotCreation(
            fixture.owner(),
            fixture.topic(),
            fixture.counter(),
            fixture.sessionId(),
            AbilityProfileRevisionReason.SESSION_COMPLETED
        )
    ));
  }

  private EpisodeFactEntity saveEpisode(EpisodeFixture fixture) {
    saveSessionIfMissing(fixture.sessionId(), fixture.owner());
    insertTurn(fixture);
    AdaptiveAgentAssessmentEntity assessment = assessmentRepository.saveAndFlush(
        new AdaptiveAgentAssessmentEntity(
            0,
            assessment(fixture.sessionId(), fixture.turnIndex(), fixture.depth())
        )
    );
    return episodeRepository.saveAndFlush(new EpisodeFactEntity(
        new EpisodeFactCreation(
            fixture.owner(),
            fixture.sessionId(),
            fixture.turnIndex(),
            fixture.topic()
        ),
        assessment
    ));
  }

  private void saveSessionIfMissing(String sessionId, MemoryOwner owner) {
    if (sessionRepository.existsById(sessionId)) {
      return;
    }
    sessionRepository.saveAndFlush(new AdaptiveAgentSessionEntity(
        new AdaptiveInterviewSession(
            sessionId,
            AdaptiveInterviewSession.RUNTIME_VERSION,
            AdaptiveSessionStatus.COMPLETED,
            1,
            2
        ),
        new AdaptiveSessionCreation(
            owner.tenantId(), sessionId, owner.candidateId(), "JD", "Resume", null, null, null
        )
    ));
  }

  private void insertTurn(EpisodeFixture fixture) {
    jdbcTemplate.update(
        """
            INSERT INTO agent_turns (
              session_id, turn_index, dimension_order, question,
              parent_turn_index, trigger_type, source_assessment_id, created_at
            ) VALUES (?, ?, 0, 'question', ?, ?, ?, ?)
            """,
        fixture.sessionId(),
        fixture.turnIndex(),
        fixture.parentTurnIndex(),
        fixture.parentTurnIndex() == null ? "PLANNED" : "ASSESSMENT_GAP",
        fixture.sourceAssessmentId(),
        Timestamp.valueOf(EPISODE_TIME)
    );
  }

  private AssessmentDecision assessment(String sessionId, int turnIndex, DepthLevel depth) {
    return new AssessmentDecision(
        sessionId, turnIndex, depth, 0.8, "评估理由不进入响应", false, List.of()
    );
  }

  private void saveTags(EpisodeFactEntity episode, List<EpisodeTagValue> values) {
    EpisodeTagSource source = new EpisodeTagSource(
        EpisodeTagSourceType.ASSESSMENT_EVIDENCE,
        episode.toDomain().assessmentId()
    );
    tagRepository.saveAllAndFlush(values.stream()
        .map(value -> new EpisodeTagEntity(episode, value, source))
        .toList());
  }

  private void alignEpisodeTimes(long firstId, long secondId) {
    jdbcTemplate.update(
        "UPDATE candidate_memory_episode_facts SET created_at = ? WHERE id IN (?, ?)",
        Timestamp.valueOf(EPISODE_TIME),
        firstId,
        secondId
    );
  }

  private AuthenticatedUser principal(UUID candidateId) {
    return new AuthenticatedUser(candidateId, UserRole.CANDIDATE);
  }
  private record ProfileFixture(
      MemoryOwner owner,
      TopicKey topic,
      String sessionId,
      AbilityCounter counter
  ) {}

  private record EpisodeFixture(
      MemoryOwner owner,
      String sessionId,
      int turnIndex,
      Integer parentTurnIndex,
      TopicKey topic,
      DepthLevel depth,
      Long sourceAssessmentId
  ) {}
}
