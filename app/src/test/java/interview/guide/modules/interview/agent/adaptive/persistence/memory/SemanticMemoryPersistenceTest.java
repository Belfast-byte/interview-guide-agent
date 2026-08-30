package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.memory.episode.AnswerHabit;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeAssistanceLevel;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeClosureStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactCreation;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagSource;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagSourceType;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagValue;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.EvaluationSemanticState;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMastery;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeSemanticState;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticAggregator;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticContributionFactory;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticContributionInput;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticStateProjector;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticStateSource;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticTrack;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.TransferStatus;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(showSql = false, properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
    SemanticMemoryPersistenceService.class,
    SemanticContributionFactory.class,
    SemanticAggregator.class,
    SemanticStateProjector.class,
    JpaSemanticStateSource.class
})
class SemanticMemoryPersistenceTest {

  private static final MemoryOwner OWNER = new MemoryOwner(null, "candidate-semantic");
  private static final TopicKey TOPIC = new TopicKey("redis", "persistence");

  @Autowired private SemanticMemoryPersistenceService service;
  @Autowired private SemanticStateSource states;
  @Autowired private SemanticContributionRepository contributions;
  @Autowired private EpisodeFactRepository episodes;
  @Autowired private EpisodeTagRepository tags;
  @Autowired private AdaptiveAgentAssessmentRepository assessments;

  @Test
  @DisplayName("Evaluation 与 Practice contribution 分轨且重复记录不新增事实")
  void shouldKeepTracksIsolatedAndContributionIdempotent() {
    EpisodeFactEntity practice = episode(new EpisodeFixture(
        1,
        new EpisodeKind(
            SessionMode.PRACTICE,
            EpisodeAssistanceLevel.FOLLOW_UP,
            EpisodeClosureStatus.RESOLVED
        ),
        DepthLevel.L2
    ));
    SemanticContributionInput practiceInput = input(practice, DepthLevel.L2);

    service.record(practiceInput);
    long firstRevision = practiceState().revision();
    service.record(practiceInput);
    assertThat(practiceState().revision()).isEqualTo(firstRevision);
    EpisodeFactEntity evaluation = episode(new EpisodeFixture(
        2,
        new EpisodeKind(
            SessionMode.EVALUATION,
            EpisodeAssistanceLevel.NONE,
            EpisodeClosureStatus.RESOLVED
        ),
        DepthLevel.L3
    ));
    service.record(input(evaluation, DepthLevel.L2));

    assertThat(contributions.count()).isEqualTo(2);
    assertThat(contributions.findAll())
        .extracting(SemanticContributionEntity::track)
        .containsExactlyInAnyOrder(
            SemanticTrack.PRACTICE_MASTERY,
            SemanticTrack.EVALUATED_CAPABILITY
        );
    PracticeSemanticState practiceState = practiceState();
    assertThat(practiceState.mastery()).isEqualTo(PracticeMastery.ASSISTED);
    assertThat(practiceState.transfer().status()).isEqualTo(TransferStatus.CONFIRMED);
    EvaluationSemanticState evaluationState = evaluationState();
    assertThat(evaluationState.statistics().count(DepthLevel.L3)).isEqualTo(1);
  }

  @Test
  @DisplayName("两个不同练习 Episode 的同标签才沉淀为稳定模式")
  void shouldPromotePatternAfterTwoEpisodes() {
    EpisodeTagValue habit = EpisodeTagValue.habit(
        AnswerHabit.IMPLEMENTATION_WITHOUT_TRADEOFF);
    EpisodeFactEntity first = practiceEpisode(11);
    EpisodeFactEntity second = practiceEpisode(12);
    service.record(input(first, DepthLevel.L2));
    service.record(input(second, DepthLevel.L2));
    tags.saveAllAndFlush(List.of(tag(first, habit, 1), tag(second, habit, 2)));

    assertThat(practiceState().stablePatterns())
        .singleElement()
        .satisfies(pattern -> {
          assertThat(pattern.value()).isEqualTo(habit);
          assertThat(pattern.episodeCount()).isEqualTo(2);
        });
  }

  private EpisodeFactEntity practiceEpisode(long index) {
    return episode(new EpisodeFixture(
        index,
        new EpisodeKind(
            SessionMode.PRACTICE,
            EpisodeAssistanceLevel.NONE,
            EpisodeClosureStatus.RESOLVED
        ),
        DepthLevel.L2
    ));
  }

  private EpisodeFactEntity episode(EpisodeFixture fixture) {
    String sessionId = "semantic-session-" + fixture.index();
    AdaptiveAgentAssessmentEntity assessment = assessments.saveAndFlush(
        new AdaptiveAgentAssessmentEntity(0, new AssessmentDecision(
            sessionId, 1, fixture.observedDepth(), 0.8, "事实", List.of())));
    return episodes.saveAndFlush(new EpisodeFactEntity(new EpisodeFactCreation(
        OWNER,
        sessionId,
        fixture.kind().mode(),
        assessment.id(),
        1,
        TOPIC,
        "target-0",
        fixture.kind().assistance(),
        fixture.kind().closure(),
        null
    ), assessment));
  }

  private SemanticContributionInput input(
      EpisodeFactEntity episode,
      DepthLevel targetDepth
  ) {
    return new SemanticContributionInput(
        episode.toDomain(), episode.toDomain().sessionMode() == SessionMode.EVALUATION
            ? DepthLevel.L3
            : DepthLevel.L2, targetDepth);
  }

  private EpisodeTagEntity tag(
      EpisodeFactEntity episode,
      EpisodeTagValue value,
      long sourceId
  ) {
    return new EpisodeTagEntity(
        episode,
        value,
        new EpisodeTagSource(EpisodeTagSourceType.ASSESSMENT_EVIDENCE, sourceId)
    );
  }

  private PracticeSemanticState practiceState() {
    return (PracticeSemanticState) states.findByOwner(OWNER).stream()
        .filter(state -> state.key().track() == SemanticTrack.PRACTICE_MASTERY)
        .findFirst()
        .orElseThrow();
  }

  private EvaluationSemanticState evaluationState() {
    return (EvaluationSemanticState) states.findByOwner(OWNER).stream()
        .filter(state -> state.key().track() == SemanticTrack.EVALUATED_CAPABILITY)
        .findFirst()
        .orElseThrow();
  }

  private record EpisodeFixture(
      long index,
      EpisodeKind kind,
      DepthLevel observedDepth
  ) {}

  private record EpisodeKind(
      SessionMode mode,
      EpisodeAssistanceLevel assistance,
      EpisodeClosureStatus closure
  ) {}
}
