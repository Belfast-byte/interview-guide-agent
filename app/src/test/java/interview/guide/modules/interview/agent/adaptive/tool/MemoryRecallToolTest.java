package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeQueryService;
import interview.guide.modules.interview.agent.adaptive.memory.episode.exposure.QuestionExposureRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MemoryRecallToolTest {
  private final EpisodeQueryService episodes = mock(EpisodeQueryService.class);
  private final QuestionExposureRepository exposures = mock(QuestionExposureRepository.class);
  private final MemoryRecallTool tool = new MemoryRecallTool(episodes, exposures);
  private final MemoryOwner owner = new MemoryOwner("tenant", "candidate");
  private final TopicKey topic = new TopicKey("java", "concurrency");

  @Test
  void recallsOriginalExperienceUsingCurrentOwnerAndTopicWithoutPurposeEnum() {
    var episode = mock(EpisodeQueryService.EpisodeView.class);
    when(episode.episodeId()).thenReturn(7L);
    when(episode.reference()).thenReturn("episode:7");
    when(episodes.recent(eq(owner), eq(topic), any())).thenReturn(List.of(episode));
    var request = request(SessionMode.PRACTICE, Map.of("targetId", "target-0"));
    tool.validate(request);
    var result = (ReadToolResult.Success) tool.execute(request);
    assertThat(result.data().get("episodes")).isEqualTo(List.of(episode));
    assertThat(result.adoptableSources()).singleElement().satisfies(source -> {
      assertThat(source.reference()).isEqualTo("episode:7");
      assertThat(source.version()).isNull();
    });
    verify(episodes).recent(eq(owner), eq(topic), any());
  }

  @Test
  void rejectsModelSuppliedOwnerAndUnknownTargetAtToolBoundary() {
    assertThatThrownBy(() -> tool.validate(request(SessionMode.PRACTICE,
        Map.of("targetId", "target-0", "owner", "other"))))
        .isInstanceOf(ReadToolValidationException.class);
    assertThatThrownBy(() -> tool.validate(request(SessionMode.PRACTICE,
        Map.of("targetId", "other")))).isInstanceOf(ReadToolValidationException.class);
    verifyNoInteractions(episodes, exposures);
  }

  @Test
  void evaluationReadsOnlyExposedQuestionsAndNeverHistoricalAbility() {
    var result = (ReadToolResult.Success) tool.execute(
        request(SessionMode.EVALUATION, Map.of("targetId", "target-0")));
    assertThat(result.data()).containsOnlyKeys("recentQuestions");
    assertThat(result.adoptableSources()).isEmpty();
    verifyNoInteractions(episodes);
  }

  @Test
  void databaseFailureIsNotReportedAsEmptyMemory() {
    var failure = new IllegalStateException("database unavailable");
    when(episodes.recent(any(), any(), any())).thenThrow(failure);
    assertThatThrownBy(() -> tool.execute(request(SessionMode.PRACTICE,
        Map.of("targetId", "target-0")))).isSameAs(failure);
  }

  private ReadToolRequest request(SessionMode mode, Map<String, Object> args) {
    var context = mock(AgentContext.class, RETURNS_DEEP_STUBS);
    var target = mock(CoverageView.TargetCoverage.class, RETURNS_DEEP_STUBS);
    when(context.session().mode()).thenReturn(mode);
    when(context.session().identity().owner()).thenReturn(owner);
    when(context.facts().coverage().targets()).thenReturn(List.of(target));
    when(target.targetId()).thenReturn("target-0");
    when(target.target().identity().topic()).thenReturn(topic);
    return new ReadToolRequest(context, args, Long.MAX_VALUE);
  }
}
