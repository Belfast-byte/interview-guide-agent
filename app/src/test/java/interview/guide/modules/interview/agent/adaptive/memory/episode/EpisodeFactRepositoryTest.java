package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactEntity.Creation;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveTurnCreation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false, properties = {
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(EpisodeQueryService.class)
class EpisodeFactRepositoryTest {
  private static final MemoryOwner OWNER = new MemoryOwner(null, "candidate-1");
  private static final TopicKey TOPIC = new TopicKey("java-backend", "CONCURRENCY");
  @Autowired private EpisodeFactRepository episodes;
  @Autowired private EpisodeQueryService query;
  @Autowired private AdaptiveAgentAssessmentRepository assessments;
  @Autowired private AdaptiveAgentTurnRepository turns;
  @Autowired private AssessmentProbeGapRepository gaps;

  @Test
  void immediatelyReadsOriginalAnswerAssessmentAndGap() {
    var saved = save(new Sample(OWNER, "session-1", TOPIC, DepthLevel.L1));
    var assessment = assessments.findById(query.latest(OWNER).getFirst().assessmentId()).orElseThrow();
    gaps.saveAndFlush(new AssessmentProbeGapEntity(assessment, 1,
        new ProbeGap("synchronized 保证互斥", "没有解释底层监视器与竞争机制")));

    assertThat(query.recent(OWNER, TOPIC, PageRequest.of(0, 5))).singleElement().satisfies(view -> {
      assertThat(view.episodeId()).isEqualTo(saved.id());
      assertThat(view.question()).isEqualTo("库存扣减使用 synchronized，竞争时发生什么？");
      assertThat(view.answer()).isEqualTo("synchronized 保证互斥");
      assertThat(view.depthLevel()).isEqualTo(DepthLevel.L1);
      assertThat(view.rationaleSummary()).isEqualTo("本次正式评估");
      assertThat(view.gaps()).singleElement().satisfies(gap ->
          assertThat(gap.missingPoint()).contains("底层监视器"));
      assertThat(view.priorTurns()).isEmpty();
      assertThat(view.expectedDepth()).isNull();
    });
  }

  @Test
  void newPracticeAppendsExperienceAndLatestDoesNotMeanHighest() {
    var old = save(new Sample(OWNER, "session-old", TOPIC, DepthLevel.L3));
    var recent = save(new Sample(OWNER, "session-new", TOPIC, DepthLevel.L1));

    assertThat(query.latest(OWNER)).singleElement().satisfies(view -> {
      assertThat(view.episodeId()).isEqualTo(recent.id());
      assertThat(view.depthLevel()).isEqualTo(DepthLevel.L1);
    });
    var history = query.recent(OWNER, TOPIC, PageRequest.of(0, 5));
    assertThat(history).extracting(EpisodeQueryService.EpisodeView::episodeId)
        .containsExactly(recent.id(), old.id());
    assertThat(history.getLast().depthLevel()).isEqualTo(DepthLevel.L3);
  }

  @Test
  void isolatesCandidateTenantAndSkillEvenWhenFocusIsTheSame() {
    save(new Sample(OWNER, "mine", TOPIC, DepthLevel.L2));
    save(new Sample(new MemoryOwner("tenant-b", OWNER.candidateId()), "tenant", TOPIC, DepthLevel.L4));
    save(new Sample(new MemoryOwner(null, "other"), "other", TOPIC, DepthLevel.L4));
    var testing = new TopicKey("test-development", TOPIC.focusId());
    save(new Sample(OWNER, "testing", testing, DepthLevel.L1));

    assertThat(query.recent(OWNER, TOPIC, PageRequest.of(0, 5)))
        .extracting(EpisodeQueryService.EpisodeView::sessionId).containsExactly("mine");
    assertThat(query.latest(OWNER)).extracting(view -> view.topic().skillId())
        .containsExactlyInAnyOrder("java-backend", "test-development");
    assertThat(query.page(OWNER, PageRequest.of(0, 1)).getTotalElements()).isEqualTo(2);
  }

  @Test
  void retainsPriorQuestionAndAnswerEvenWithoutOldEpisodeIndex() {
    var parent = turn("hinted", 1);
    var followUp = turn("hinted", 2);
    var assessment = assessment("hinted", 2, DepthLevel.L2);
    episodes.saveAndFlush(new EpisodeFactEntity(new Creation(
        OWNER, "hinted", SessionMode.PRACTICE, followUp.id(), 2, TOPIC, "target-0"), assessment));

    assertThat(query.latest(OWNER)).singleElement().satisfies(view ->
        assertThat(view.priorTurns()).singleElement().satisfies(prior -> {
          assertThat(prior.question()).isEqualTo(parent.question());
          assertThat(prior.answer()).isEqualTo(parent.answer());
        }));
  }

  @Test
  void readsExistingClosureEvidenceWithoutChangingOldAnswerGrade() {
    var old = save(new Sample(OWNER, "old", TOPIC, DepthLevel.L1));
    var first = assessments.findById(query.latest(OWNER).getFirst().assessmentId()).orElseThrow();
    var gap = gaps.saveAndFlush(new AssessmentProbeGapEntity(first, 1, new ProbeGap("互斥", "锁竞争机制")));
    var second = assessment("old", 2, DepthLevel.L3);
    gap.closeByEvidence(second, "竞争失败的线程会阻塞等待", "补充了竞争过程");
    gaps.flush();

    assertThat(query.latest(OWNER)).singleElement().satisfies(view -> {
      assertThat(view.depthLevel()).isEqualTo(DepthLevel.L1);
      assertThat(view.gaps()).singleElement().satisfies(item -> {
        assertThat(item.closedByAssessmentId()).isEqualTo(second.id());
        assertThat(item.closureEvidenceQuote()).isEqualTo("竞争失败的线程会阻塞等待");
      });
    });
  }

  @Test
  void databaseStillRejectsDuplicateAnswerIndex() {
    var saved = save(new Sample(OWNER, "once", TOPIC, DepthLevel.L2));
    var fact = query.latest(OWNER).getFirst();
    var duplicate = new EpisodeFactEntity(new Creation(OWNER, fact.sessionId(),
        fact.sessionMode(), turns.findBySessionIdAndTurnIndex("once", 1).orElseThrow().id(), fact.turnIndex(), TOPIC, "target-0"),
        assessments.findById(fact.assessmentId()).orElseThrow());
    assertThatThrownBy(() -> episodes.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private EpisodeFactEntity save(Sample sample) {
    var turn = turn(sample.session(), 1);
    var assessment = assessment(sample.session(), 1, sample.level());
    return episodes.saveAndFlush(new EpisodeFactEntity(new Creation(
        sample.owner(), sample.session(), SessionMode.PRACTICE, turn.id(), 1,
        sample.topic(), "target-0"), assessment));
  }

  private AdaptiveAgentTurnEntity turn(String session, int index) {
    var turn = new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(session, index, 0,
        RespondAction.ask("库存扣减使用 synchronized，竞争时发生什么？", "验证锁机制"),
        index == 1 ? TurnProvenance.initial() : TurnProvenance.agentDecision(index - 1),
        WorkingMemory.empty()));
    turn.recordAnswer(new CandidateAnswer(index, "synchronized 保证互斥"));
    return turns.saveAndFlush(turn);
  }

  private AdaptiveAgentAssessmentEntity assessment(String session, int index, DepthLevel level) {
    return assessments.saveAndFlush(new AdaptiveAgentAssessmentEntity(0,
        new AssessmentDecision(session, index, level, 0.8, "本次正式评估", List.of())));
  }

  private record Sample(MemoryOwner owner, String session, TopicKey topic, DepthLevel level) {}
}
