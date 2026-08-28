package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.episode.AnswerHabit;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentCompletion;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFact;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactCreation;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeAssistanceLevel;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeClosureStatus;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagSource;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagSourceType;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagValue;
import interview.guide.modules.interview.agent.adaptive.memory.episode.ErrorPattern;
import interview.guide.modules.interview.agent.adaptive.memory.episode.ValidatedEpisodeTag;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(EpisodeEnrichmentPersistenceService.class)
class EpisodeEnrichmentPersistenceTest {

  @Autowired
  private EpisodeEnrichmentPersistenceService service;

  @Autowired
  private EpisodeFactRepository episodeRepository;

  @Autowired
  private EpisodeTagRepository tagRepository;

  @Autowired
  private AdaptiveAgentAssessmentRepository assessmentRepository;

  @Autowired
  private EntityManager entityManager;

  private EpisodeFactEntity episode;

  @BeforeEach
  void createEpisode() {
    AdaptiveAgentAssessmentEntity assessment = assessmentRepository.saveAndFlush(
        new AdaptiveAgentAssessmentEntity(0, assessment())
    );
    episode = episodeRepository.saveAndFlush(new EpisodeFactEntity(
        new EpisodeFactCreation(
            new MemoryOwner(null, "candidate-enrichment"),
            "session-enrichment",
            SessionMode.EVALUATION,
            1,
            1,
            new TopicKey("java-backend", "REDIS"),
            "target-0",
            1,
            2,
            EpisodeAssistanceLevel.NONE,
            EpisodeClosureStatus.UNRESOLVED,
            null
        ),
        assessment
    ));
  }

  @Test
  @DisplayName("PENDING 只允许一个 worker claim")
  void shouldClaimOnlyOnce() {
    assertThat(service.claim(episode.id())).isPresent();
    assertThat(service.claim(episode.id())).isEmpty();

    assertThat(reload().enrichmentStatus())
        .isEqualTo(EpisodeEnrichmentStatus.PROCESSING);
  }

  @Test
  @DisplayName("完成时在一个事务内替换摘要和规范化标签")
  void shouldReplaceEnrichmentPayload() {
    tagRepository.saveAndFlush(tag(EpisodeTagValue.error(ErrorPattern.CONFUSES_CONCEPTS)));
    service.claim(episode.id());

    service.complete(new EpisodeEnrichmentCompletion(
        episode.id(),
        "候选人给出了版本号方案并说明了失败边界。",
        List.of(validatedTag())
    ));

    assertThat(reload()).satisfies(fact -> {
      assertThat(fact.enrichmentStatus()).isEqualTo(EpisodeEnrichmentStatus.COMPLETED);
      assertThat(fact.answerSummary()).isEqualTo("候选人给出了版本号方案并说明了失败边界。");
      assertThat(fact.enrichmentError()).isNull();
    });
    assertThat(tagRepository.findByEpisodeIdOrderById(episode.id()))
        .extracting(entity -> entity.toDomain().value())
        .containsExactly(EpisodeTagValue.habit(AnswerHabit.STRUCTURED_REASONING));
  }

  @Test
  @DisplayName("失败明确落库且不会被普通 claim 重试")
  void shouldPersistFailureWithoutImplicitRetry() {
    service.claim(episode.id());
    service.fail(episode.id(), "LLM unavailable");

    assertThat(service.claim(episode.id())).isEmpty();
    assertThat(reload()).satisfies(fact -> {
      assertThat(fact.enrichmentStatus()).isEqualTo(EpisodeEnrichmentStatus.FAILED);
      assertThat(fact.enrichmentError()).isEqualTo("LLM unavailable");
      assertThat(fact.answerSummary()).isNull();
    });

  }

  @Test
  @DisplayName("空摘要不能伪装为 enrichment 成功")
  void shouldRejectBlankSummary() {
    service.claim(episode.id());

    assertThatThrownBy(() -> service.complete(new EpisodeEnrichmentCompletion(
        episode.id(),
        " ",
        List.of()
    ))).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("answerSummary");
  }

  private AssessmentDecision assessment() {
    return new AssessmentDecision(
        "session-enrichment",
        1,
        DepthLevel.L2,
        0.8,
        "基础回答",
        List.of()
    );
  }

  private EpisodeTagEntity tag(EpisodeTagValue value) {
    return new EpisodeTagEntity(
        episode,
        value,
        new EpisodeTagSource(EpisodeTagSourceType.ASSESSMENT_EVIDENCE, 7)
    );
  }

  private ValidatedEpisodeTag validatedTag() {
    return new ValidatedEpisodeTag(
        EpisodeTagValue.habit(AnswerHabit.STRUCTURED_REASONING),
        new EpisodeTagSource(EpisodeTagSourceType.PROBE_GAP, 8)
    );
  }

  private EpisodeFact reload() {
    entityManager.flush();
    entityManager.clear();
    return episodeRepository.findById(episode.id()).orElseThrow().toDomain();
  }
}
