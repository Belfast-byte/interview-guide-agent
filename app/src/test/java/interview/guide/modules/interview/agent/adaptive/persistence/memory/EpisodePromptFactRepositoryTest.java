package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.EpisodePromptFact;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.AnswerHabit;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodePromptCandidate;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodePromptFactSource;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagSource;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagSourceType;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagValue;
import interview.guide.modules.interview.agent.adaptive.memory.episode.ErrorPattern;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactCreation;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
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
@Import(JpaEpisodePromptFactSource.class)
class EpisodePromptFactRepositoryTest {

  private static final MemoryOwner CANDIDATE = new MemoryOwner(null, "candidate-prompt");
  private static final MemoryOwner OTHER_CANDIDATE = new MemoryOwner(null, "candidate-other");
  private static final MemoryOwner TENANT_CANDIDATE = new MemoryOwner(
      "tenant-a",
      "candidate-prompt"
  );
  private static final TopicKey REDIS = new TopicKey("java-backend", "REDIS");
  private static final TopicKey JVM = new TopicKey("java-backend", "JVM");
  private static final TopicKey REACT = new TopicKey("frontend", "REACT");

  @Autowired
  private EpisodePromptFactSource source;

  @Autowired
  private AdaptiveAgentSessionRepository sessionRepository;

  @Autowired
  private AdaptiveAgentAssessmentRepository assessmentRepository;

  @Autowired
  private EpisodeFactRepository episodeRepository;

  @Autowired
  private EpisodeTagRepository tagRepository;

  @Test
  @DisplayName("只读取同 owner、同 skill 的已完成历史 Episode 并排除当前场")
  void shouldReadOnlyCompletedOwnerHistoryAndExcludeCurrentSession() {
    saveSession("current", CANDIDATE, AdaptiveSessionStatus.IN_PROGRESS);
    saveEpisode(new EpisodeFixture(
        "history-redis", CANDIDATE, REDIS, DepthLevel.L3, true, true
    ));
    saveEpisode(new EpisodeFixture(
        "history-jvm", CANDIDATE, JVM, DepthLevel.L2, true, true
    ));
    saveEpisode(new EpisodeFixture(
        "history-other-skill", CANDIDATE, REACT, DepthLevel.L4, true, true
    ));
    saveEpisode(new EpisodeFixture(
        "history-other-owner", OTHER_CANDIDATE, REDIS, DepthLevel.L4, true, true
    ));
    saveEpisode(new EpisodeFixture(
        "history-tenant", TENANT_CANDIDATE, REDIS, DepthLevel.L4, true, true
    ));
    saveEpisode(new EpisodeFixture(
        "history-active", CANDIDATE, REDIS, DepthLevel.L1, false, true
    ));
    saveEpisode(new EpisodeFixture(
        "history-pending", CANDIDATE, REDIS, DepthLevel.L1, true, false
    ));
    saveEpisode(new EpisodeFixture(
        "current", CANDIDATE, REDIS, DepthLevel.L4, false, true
    ));

    List<EpisodePromptCandidate> result = source.findCompletedHistory(
        "current",
        REDIS.skillId()
    );

    assertThat(result).hasSize(2)
        .extracting(candidate -> candidate.fact().focusId())
        .containsExactlyInAnyOrder("REDIS", "JVM");
    assertThat(result.stream()
        .filter(candidate -> candidate.fact().focusId().equals("REDIS"))
        .findFirst()
        .orElseThrow()
        .fact()).satisfies(fact -> {
          assertThat(fact.depthLevel()).isEqualTo(DepthLevel.L3);
          assertThat(fact.errorTags()).containsExactly("MISSING_FAILURE_BOUNDARY");
          assertThat(fact.answerHabitTags()).containsExactly("STRUCTURED_REASONING");
        });
  }

  @Test
  @DisplayName("相同 candidateId 在租户与非租户之间严格隔离")
  void shouldIsolateTenantOwner() {
    saveSession("tenant-current", TENANT_CANDIDATE, AdaptiveSessionStatus.IN_PROGRESS);
    saveEpisode(new EpisodeFixture(
        "candidate-history", CANDIDATE, REDIS, DepthLevel.L1, true, true
    ));
    saveEpisode(new EpisodeFixture(
        "tenant-history", TENANT_CANDIDATE, REDIS, DepthLevel.L4, true, true
    ));

    assertThat(source.findCompletedHistory("tenant-current", REDIS.skillId()))
        .singleElement()
        .extracting(candidate -> candidate.fact().depthLevel())
        .isEqualTo(DepthLevel.L4);
  }

  private void saveEpisode(EpisodeFixture fixture) {
    if (!sessionRepository.existsById(fixture.sessionId())) {
      saveSession(
          fixture.sessionId(),
          fixture.owner(),
          fixture.completedSession()
              ? AdaptiveSessionStatus.COMPLETED
              : AdaptiveSessionStatus.IN_PROGRESS
      );
    }
    AdaptiveAgentAssessmentEntity assessment = assessmentRepository.saveAndFlush(
        new AdaptiveAgentAssessmentEntity(
            0,
            assessment(fixture.sessionId(), fixture.depth())
        )
    );
    EpisodeFactEntity episode = new EpisodeFactEntity(
        new EpisodeFactCreation(
            fixture.owner(),
            fixture.sessionId(),
            1,
            fixture.topic()
        ),
        assessment
    );
    if (fixture.completedEnrichment()) {
      episode.claimEnrichment();
      episode.completeEnrichment("不进入 Prompt 的摘要");
    }
    episodeRepository.saveAndFlush(episode);
    if (fixture.completedEnrichment()) {
      saveTags(episode, assessment.id());
    }
  }

  private AssessmentDecision assessment(String sessionId, DepthLevel depth) {
    return new AssessmentDecision(
        sessionId,
        1,
        depth,
        0.8,
        "不进入 Prompt 的评估理由",
        false,
        List.of()
    );
  }

  private void saveTags(EpisodeFactEntity episode, long sourceId) {
    EpisodeTagSource source = new EpisodeTagSource(
        EpisodeTagSourceType.ASSESSMENT_EVIDENCE,
        sourceId
    );
    tagRepository.saveAllAndFlush(List.of(
        new EpisodeTagEntity(
            episode,
            EpisodeTagValue.error(ErrorPattern.MISSING_FAILURE_BOUNDARY),
            source
        ),
        new EpisodeTagEntity(
            episode,
            EpisodeTagValue.habit(AnswerHabit.STRUCTURED_REASONING),
            source
        )
    ));
  }

  private void saveSession(
      String sessionId,
      MemoryOwner owner,
      AdaptiveSessionStatus status
  ) {
    sessionRepository.saveAndFlush(new AdaptiveAgentSessionEntity(
        new AdaptiveInterviewSession(
            sessionId,
            AdaptiveInterviewSession.RUNTIME_VERSION,
            status,
            1,
            1
        ),
        new AdaptiveSessionCreation(
            owner.tenantId(),
            sessionId,
            owner.candidateId(),
            "JD",
            "Resume",
            null,
            null,
            null
        )
    ));
  }

  private record EpisodeFixture(
      String sessionId,
      MemoryOwner owner,
      TopicKey topic,
      DepthLevel depth,
      boolean completedSession,
      boolean completedEnrichment
  ) {}
}
