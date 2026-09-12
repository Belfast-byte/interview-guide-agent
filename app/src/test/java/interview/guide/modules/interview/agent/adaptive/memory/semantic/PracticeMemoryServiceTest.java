package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeQueryService;
import interview.guide.modules.interview.skill.InterviewSkillService;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PracticeMemoryServiceTest {
  private final EpisodeQueryService episodes = mock(EpisodeQueryService.class);
  private final InterviewSkillService skills = mock(InterviewSkillService.class);
  private final PracticeMemoryService memory = new PracticeMemoryService(episodes, skills);
  private final MemoryOwner owner = new MemoryOwner(null, "candidate");
  private final TopicKey topic = new TopicKey("java-backend", "CONCURRENCY");

  @Test
  void practiceUsesExistingExperienceOnlyWithinSelectedScope() {
    var latest = episode(topic);
    when(episodes.recent(eq(owner), eq(topic), any())).thenReturn(List.of(latest));
    var history = memory.planning(owner, new PracticeScope(List.of(topic)));
    assertThat(history.topics()).singleElement().satisfies(item -> {
      assertThat(item.topic()).isEqualTo(topic);
      assertThat(item.episodes()).containsExactly(latest);
    });
    verify(episodes).recent(eq(owner), eq(topic), any());
    verifyNoMoreInteractions(episodes);
  }

  @Test
  void profileGroupsBySkillAndLeavesUnassessedTopicsWithoutGrade() {
    var latest = episode(topic);
    var testing = episode(new TopicKey("testing", "CONCURRENCY"));
    when(episodes.latest(owner)).thenReturn(List.of(latest, testing));
    when(skills.getAllSkills()).thenReturn(List.of(skill("Java 开发")));

    var profile = memory.profile(owner);
    assertThat(profile).hasSize(2);
    assertThat(profile.getFirst().skillId()).isEqualTo("java-backend");
    assertThat(profile.getFirst().skillName()).isEqualTo("Java 开发");
    assertThat(profile.getFirst().topics().getFirst().latest()).isSameAs(latest);
    assertThat(profile.getFirst().topics().getLast().latest()).isNull();
    assertThat(profile.getLast().topics().getFirst().latest()).isSameAs(testing);

    when(skills.getAllSkills()).thenReturn(List.of(skill("Java 工程师")));
    assertThat(memory.profile(owner).getFirst().skillId()).isEqualTo("java-backend");
  }

  @Test
  void planningExposesQueryErrorsWithoutEmptyFallback() {
    var failure = new IllegalStateException("database unavailable");
    when(episodes.recent(any(), any(), any())).thenThrow(failure);
    assertThatThrownBy(() -> memory.planning(owner, new PracticeScope(List.of(topic))))
        .isSameAs(failure);
  }

  private EpisodeQueryService.EpisodeView episode(TopicKey key) {
    var episode = mock(EpisodeQueryService.EpisodeView.class);
    when(episode.topic()).thenReturn(key);
    return episode;
  }

  private InterviewSkillService.SkillDTO skill(String name) {
    return new InterviewSkillService.SkillDTO("java-backend", name, "Java 开发", List.of(
        new InterviewSkillService.SkillCategoryDTO("CONCURRENCY", "并发", "high", null, false),
        new InterviewSkillService.SkillCategoryDTO("JVM", "虚拟机", "high", null, false)),
        true, null, null, null);
  }
}
