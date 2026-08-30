package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactCreation;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeAssistanceLevel;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeClosureStatus;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(showSql = false, properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class EpisodeFactRepositoryTest {

  private static final TopicKey TOPIC = new TopicKey("java-backend", "REDIS");

  @Autowired
  private EpisodeFactRepository episodeRepository;

  @Autowired
  private AdaptiveAgentAssessmentRepository assessmentRepository;

  @Test
  @DisplayName("Episode 保存不可变会话、Turn、Assessment 和目标事实")
  void shouldPersistImmutableFact() {
    EpisodeFactEntity saved = saveEpisode(
        new MemoryOwner(null, "candidate-1"),
        "session-1",
        1
    );

    assertThat(saved.toDomain()).satisfies(fact -> {
      assertThat(fact.owner()).isEqualTo(new MemoryOwner(null, "candidate-1"));
      assertThat(fact.topic()).isEqualTo(TOPIC);
      assertThat(fact.sessionMode()).isEqualTo(SessionMode.EVALUATION);
      assertThat(fact.targetId()).isEqualTo("target-0");
      assertThat(fact.assistanceLevel()).isEqualTo(EpisodeAssistanceLevel.NONE);
      assertThat(fact.closureStatus()).isEqualTo(EpisodeClosureStatus.UNRESOLVED);
      assertThat(fact.enrichmentStatus()).isEqualTo(EpisodeEnrichmentStatus.PENDING);
      assertThat(fact.enrichmentError()).isNull();
    });
  }

  @Test
  @DisplayName("相同 session 和 turn 只能存在一个 Episode")
  void shouldRejectDuplicateTurn() {
    MemoryOwner owner = new MemoryOwner(null, "candidate-1");
    AdaptiveAgentAssessmentEntity assessment = saveAssessment("session-1", 1);
    episodeRepository.saveAndFlush(episode(owner, "session-1", assessment));

    assertThatThrownBy(() -> episodeRepository.saveAndFlush(
        episode(owner, "session-1", assessment)
    ))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("Episode 查询按 tenant 和 candidate 双重隔离")
  void shouldIsolateByOwner() {
    saveEpisode(new MemoryOwner("tenant-a", "candidate-1"), "session-a", 1);
    saveEpisode(new MemoryOwner("tenant-b", "candidate-1"), "session-b", 1);
    saveEpisode(new MemoryOwner(null, "candidate-1"), "session-c", 1);

    assertThat(episodeRepository
        .findByTenantIdAndCandidateIdOrderByCreatedAtDescIdDesc(
            "tenant-a",
            "candidate-1"
        )).hasSize(1);
    assertThat(episodeRepository
        .findByTenantIdIsNullAndCandidateIdOrderByCreatedAtDescIdDesc("candidate-1"))
        .hasSize(1);
  }

  private EpisodeFactEntity saveEpisode(
      MemoryOwner owner,
      String sessionId,
      int turnIndex
  ) {
    return episodeRepository.saveAndFlush(episode(
        owner,
        sessionId,
        saveAssessment(sessionId, turnIndex)
    ));
  }

  private EpisodeFactEntity episode(
      MemoryOwner owner,
      String sessionId,
      AdaptiveAgentAssessmentEntity assessment
  ) {
    return new EpisodeFactEntity(
        new EpisodeFactCreation(
            owner,
            sessionId,
            SessionMode.EVALUATION,
            turnId(sessionId),
            assessment.turnIndex(),
            TOPIC,
            "target-0",
            EpisodeAssistanceLevel.NONE,
            EpisodeClosureStatus.UNRESOLVED,
            null
        ),
        assessment
    );
  }

  private long turnId(String sessionId) {
    return Math.abs((long) sessionId.hashCode());
  }

  private AdaptiveAgentAssessmentEntity saveAssessment(String sessionId, int turnIndex) {
    return assessmentRepository.saveAndFlush(
        new AdaptiveAgentAssessmentEntity(0, assessment(sessionId, turnIndex))
    );
  }

  private AssessmentDecision assessment(String sessionId, int turnIndex) {
    return new AssessmentDecision(
        sessionId,
        turnIndex,
        DepthLevel.L2,
        0.8,
        "基础回答",
        List.of()
    );
  }
}
