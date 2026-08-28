package interview.guide.modules.interview.agent.adaptive.api;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.common.security.AuthenticatedUser;
import interview.guide.modules.auth.domain.UserRole;
import interview.guide.modules.interview.agent.adaptive.application.CandidateMemoryQueryService;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeAssistanceLevel;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.EvaluationContribution;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeContribution;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeOutcome;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeResult;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticAggregator;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticSource;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticStateKey;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticTrack;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.JpaSemanticStateSource;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.SemanticStateEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.SemanticStateRepository;
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

@DataJpaTest(showSql = false, properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({CandidateMemoryQueryService.class, JpaSemanticStateSource.class})
@DisplayName("候选人双轨长期记忆查询接口")
class CandidateMemoryControllerTest {

  private static final UUID CANDIDATE_ID = UUID.fromString(
      "11111111-1111-1111-1111-111111111111");
  private static final MemoryOwner OWNER = new MemoryOwner(null, CANDIDATE_ID.toString());
  private static final TopicKey REDIS = new TopicKey("java-backend", "REDIS");
  private static final LocalDateTime BASE = LocalDateTime.of(2026, 8, 28, 10, 0);

  @Autowired private CandidateMemoryQueryService queryService;
  @Autowired private SemanticStateRepository states;
  private CandidateMemoryController controller;
  private final SemanticAggregator aggregator = new SemanticAggregator();

  @BeforeEach
  void setUp() {
    controller = new CandidateMemoryController(queryService);
  }

  @Test
  @DisplayName("直接返回正式能力和练习掌握双轨状态")
  void shouldReturnDualTrackSemanticState() {
    saveEvaluation(OWNER);
    savePractice(OWNER);
    saveEvaluation(new MemoryOwner("tenant-a", OWNER.candidateId()));

    CandidateMemoryResponse response = controller.get(principal(CANDIDATE_ID), 0).getData();

    assertThat(response.topics()).singleElement().satisfies(topic -> {
      assertThat(topic.skillId()).isEqualTo("java-backend");
      assertThat(topic.evaluation().ability().name()).isEqualTo("COMPETENT");
      assertThat(topic.evaluation().statistics().levelCounts())
          .containsExactly(0L, 0L, 1L, 0L, 0L);
      assertThat(topic.practice().mastery().name()).isEqualTo("ASSISTED");
      assertThat(topic.practice().details().latest().result().assistance())
          .isEqualTo(EpisodeAssistanceLevel.FOLLOW_UP);
      assertThat(topic.practice().details().transfer().status().name())
          .isEqualTo("NOT_REEVALUATED");
    });
  }

  @Test
  @DisplayName("无长期记忆时返回空双轨主题和空分页")
  void shouldReturnEmptyMemory() {
    UUID unknown = UUID.fromString("33333333-3333-3333-3333-333333333333");

    CandidateMemoryResponse response = controller.get(principal(unknown), 0).getData();

    assertThat(response.candidateId()).isEqualTo(unknown.toString());
    assertThat(response.topics()).isEmpty();
    assertThat(response.episodes().content()).isEmpty();
  }

  @Test
  @DisplayName("Episode 响应不暴露题答、摘要、评估依据或内部来源")
  void shouldExposeOnlyEpisodeIndexFields() {
    assertThat(Arrays.stream(CandidateMemoryResponse.EpisodeResponse.class
        .getRecordComponents()).map(component -> component.getName()))
        .containsExactly(
            "sessionId", "turnIndex", "parentTurnIndex", "triggerType",
            "skillId", "focusId", "depthLevel", "enrichmentStatus", "createdAt");
  }

  private void saveEvaluation(MemoryOwner owner) {
    EvaluationContribution contribution = new EvaluationContribution(
        source(owner, 1), DepthLevel.L2);
    SemanticStateEntity state = new SemanticStateEntity(new SemanticStateKey(
        owner, REDIS, SemanticTrack.EVALUATED_CAPABILITY));
    state.apply(aggregator.evaluation(List.of(contribution), List.of()));
    states.saveAndFlush(state);
  }

  private void savePractice(MemoryOwner owner) {
    PracticeContribution contribution = new PracticeContribution(
        source(owner, 2),
        new PracticeResult(
            PracticeOutcome.COMPLETED,
            EpisodeAssistanceLevel.FOLLOW_UP,
            DepthLevel.L2
        ));
    SemanticStateEntity state = new SemanticStateEntity(new SemanticStateKey(
        owner, REDIS, SemanticTrack.PRACTICE_MASTERY));
    state.apply(aggregator.practice(List.of(contribution), List.of(), List.of()));
    states.saveAndFlush(state);
  }

  private SemanticSource source(MemoryOwner owner, long episodeId) {
    return new SemanticSource(episodeId, owner, REDIS, BASE.plusMinutes(episodeId));
  }

  private AuthenticatedUser principal(UUID candidateId) {
    return new AuthenticatedUser(candidateId, UserRole.CANDIDATE);
  }
}
