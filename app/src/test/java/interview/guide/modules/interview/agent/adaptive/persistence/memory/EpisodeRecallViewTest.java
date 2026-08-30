package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeAssistanceLevel;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeClosureStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactCreation;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EvaluationRecallView;
import interview.guide.modules.interview.agent.adaptive.memory.episode.PracticeDiagnosticView;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionIdentity;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionPublication;
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
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveTurnCreation;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest(showSql = false, properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(JpaEpisodeRecallSource.class)
class EpisodeRecallViewTest {

  private static final String SESSION_ID = "episode-recall-session";
  private static final TopicKey TOPIC =
      new TopicKey("java-backend", "REDIS_PERSISTENCE");

  @Autowired private JpaEpisodeRecallSource source;
  @Autowired private AdaptiveAgentSessionRepository sessions;
  @Autowired private AdaptiveAgentTurnRepository turns;
  @Autowired private AdaptiveAgentAssessmentRepository assessments;
  @Autowired private AdaptiveAgentEvidenceRepository evidences;
  @Autowired private AssessmentProbeGapRepository gaps;
  @Autowired private EpisodeFactRepository episodes;
  @Autowired private QuestionExposureRepository exposures;

  @MockitoBean private QuestionSimilaritySearch similaritySearch;

  @BeforeEach
  void saveEpisode() {
    AdaptiveAgentSessionEntity session = sessions.saveAndFlush(session());
    AdaptiveAgentTurnEntity turn = turns.saveAndFlush(answeredTurn());
    AdaptiveAgentAssessmentEntity assessment = assessments.saveAndFlush(assessment());
    evidences.save(new AdaptiveAgentEvidenceEntity(
        assessment,
        SESSION_ID,
        1,
        new ValidatedAssessmentEvidence(EvidenceType.QUOTE, "每秒 fsync", null)
    ));
    gaps.save(new AssessmentProbeGapEntity(
        assessment, 1, new ProbeGap("每秒 fsync", "没有说明故障窗口")));
    EpisodeFactEntity episode = episodes.saveAndFlush(new EpisodeFactEntity(
        creation(turn.id()), assessment));
    QuestionExposureEntity exposure = exposures.saveAndFlush(new QuestionExposureEntity(
        new QuestionExposureCreation(
            new MemoryOwner(null, "candidate-1"),
            SESSION_ID,
            turn.id(),
            publication(),
            "question-exposure:recall"
        )
    ));
    saveUnrelatedExposure(turn.id() + 100, new MemoryOwner(null, "candidate-2"), TOPIC);
    saveUnrelatedExposure(
        turn.id() + 200,
        new MemoryOwner(null, "candidate-1"),
        new TopicKey("java-backend", "REDIS_CLUSTER")
    );
    when(similaritySearch.search(any(), any(), any())).thenReturn(List.of(
        new QuestionSimilarityHit(exposure.id(), 0.92)));
    assertThat(session.id()).isEqualTo(SESSION_ID);
    assertThat(episode.id()).isPositive();
  }

  @Test
  @DisplayName("正式召回只有中性题目和未闭环验证点")
  void shouldExposeNeutralEvaluationView() {
    List<EvaluationRecallView> recalled = source.evaluation(
        SESSION_ID, TOPIC, "Redis AOF 有什么数据丢失窗口？");

    assertThat(recalled).singleElement().satisfies(view -> {
      assertThat(view.question()).contains("AOF");
      assertThat(view.revalidationNeed()).isEqualTo("没有说明故障窗口");
      assertThat(view.similarity()).isEqualTo(0.92);
    });
    assertThat(componentNames(EvaluationRecallView.class))
        .doesNotContain("answer", "rating", "evidence", "gaps", "assistanceLevel");
  }

  @Test
  @DisplayName("练习召回包含旧回答评级证据缺口和辅助闭环状态")
  void shouldExposeFullPracticeDiagnostic() {
    List<PracticeDiagnosticView> recalled = source.practice(
        SESSION_ID, TOPIC, "Redis AOF 有什么数据丢失窗口？");

    assertThat(recalled).singleElement().satisfies(view -> {
      assertThat(view.answer()).contains("每秒刷盘");
      assertThat(view.rating()).isEqualTo(DepthLevel.L2);
      assertThat(view.evidence()).containsExactly("QUOTE: 每秒 fsync");
      assertThat(view.gaps()).extracting(ProbeGap::missingPoint)
          .containsExactly("没有说明故障窗口");
      assertThat(view.assistanceLevel()).isEqualTo(EpisodeAssistanceLevel.NONE);
      assertThat(view.closureStatus()).isEqualTo(EpisodeClosureStatus.UNRESOLVED);
    });
  }

  private AdaptiveAgentSessionEntity session() {
    return new AdaptiveAgentSessionEntity(
        AdaptiveInterviewSession.create(SESSION_ID, 2, EVALUATION_SETTINGS),
        new AdaptiveSessionCreation(
            null, SESSION_ID, "candidate-1", "JD", "Resume", "provider",
            "Provider", "model", EVALUATION_SETTINGS)
    );
  }

  private AdaptiveAgentTurnEntity answeredTurn() {
    AdaptiveAgentTurnEntity turn = new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(
        SESSION_ID, 1, 0, publication().action(), TurnProvenance.initial()));
    turn.recordAnswer(new CandidateAnswer(1, "AOF everysec 每秒刷盘，可能丢一秒"));
    return turn;
  }

  private AdaptiveAgentAssessmentEntity assessment() {
    return new AdaptiveAgentAssessmentEntity(0, new AssessmentDecision(
        SESSION_ID, 1, DepthLevel.L2, 0.8, "说明了刷盘策略", List.of()));
  }

  private EpisodeFactCreation creation(long turnId) {
    return new EpisodeFactCreation(
        new MemoryOwner(null, "candidate-1"),
        SESSION_ID,
        SessionMode.EVALUATION,
        turnId,
        1,
        TOPIC,
        "target-0",
        EpisodeAssistanceLevel.NONE,
        EpisodeClosureStatus.UNRESOLVED,
        null
    );
  }

  private QuestionPublication publication() {
    return publication(TOPIC, "Redis AOF everysec 策略在故障时会丢多少数据？");
  }

  private QuestionPublication publication(TopicKey topic, String questionText) {
    RespondAction question = RespondAction.ask(
        questionText, "验证持久化边界");
    return new QuestionPublication(question, new QuestionIdentity(
        topic,
        "解释数据丢失边界",
        DepthLevel.L2,
        "L2",
        "scenario",
        "wording"
    ), null, null);
  }

  private void saveUnrelatedExposure(
      long turnId,
      MemoryOwner owner,
      TopicKey topic
  ) {
    exposures.save(new QuestionExposureEntity(new QuestionExposureCreation(
        owner,
        "unrelated-" + turnId,
        turnId,
        publication(topic, "另一个候选人的历史问题 " + turnId),
        "question-exposure:" + turnId
    )));
  }

  private List<String> componentNames(Class<?> type) {
    return Arrays.stream(type.getRecordComponents())
        .map(RecordComponent::getName)
        .toList();
  }
}
