package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.episode.AnswerHabit;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagCategory;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagSource;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagSourceType;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagValue;
import interview.guide.modules.interview.agent.adaptive.memory.episode.ErrorPattern;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactCreation;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class EpisodeTagRepositoryTest {

  @Autowired
  private EpisodeFactRepository episodeRepository;

  @Autowired
  private EpisodeTagRepository tagRepository;

  @Autowired
  private AdaptiveAgentAssessmentRepository assessmentRepository;

  @Test
  @DisplayName("错误模式和回答习惯标签按 Episode 稳定读取")
  void shouldPersistNormalizedTags() {
    EpisodeFactEntity episode = episode();
    tagRepository.saveAllAndFlush(List.of(
        tag(episode, EpisodeTagValue.error(ErrorPattern.MISSING_FAILURE_BOUNDARY)),
        tag(episode, EpisodeTagValue.habit(AnswerHabit.STRUCTURED_REASONING))
    ));

    assertThat(tagRepository.findByEpisodeIdOrderById(episode.id()))
        .extracting(entity -> entity.toDomain().value())
        .containsExactly(
            EpisodeTagValue.error(ErrorPattern.MISSING_FAILURE_BOUNDARY),
            EpisodeTagValue.habit(AnswerHabit.STRUCTURED_REASONING)
        );
  }

  @Test
  @DisplayName("相同 Episode 标签来源关系不允许重复")
  void shouldRejectDuplicateTagSource() {
    EpisodeFactEntity episode = episode();
    EpisodeTagValue value = EpisodeTagValue.error(ErrorPattern.UNSUPPORTED_ASSUMPTION);
    tagRepository.saveAndFlush(tag(episode, value));

    assertThatThrownBy(() -> tagRepository.saveAndFlush(tag(episode, value)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("标签分类和值不匹配时在领域边界拒绝")
  void shouldRejectMismatchedCategory() {
    assertThatThrownBy(() -> new EpisodeTagValue(
        EpisodeTagCategory.ERROR_PATTERN,
        AnswerHabit.STRUCTURED_REASONING.name()
    )).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("白名单");
    assertThat(ErrorPattern.values()).hasSize(8);
    assertThat(AnswerHabit.values()).hasSize(8);
  }

  private EpisodeTagEntity tag(EpisodeFactEntity episode, EpisodeTagValue value) {
    return new EpisodeTagEntity(
        episode,
        value,
        new EpisodeTagSource(EpisodeTagSourceType.ASSESSMENT_EVIDENCE, 7)
    );
  }

  private EpisodeFactEntity episode() {
    AdaptiveAgentAssessmentEntity assessment = assessmentRepository.saveAndFlush(
        new AdaptiveAgentAssessmentEntity(0, new AssessmentDecision(
            "session-tag",
            1,
            DepthLevel.L2,
            0.8,
            "基础回答",
            false,
            List.of()
        ))
    );
    return episodeRepository.saveAndFlush(new EpisodeFactEntity(
        new EpisodeFactCreation(
            new MemoryOwner(null, "candidate-tag"),
            "session-tag",
            1,
            new TopicKey("java-backend", "REDIS")
        ),
        assessment
    ));
  }
}
