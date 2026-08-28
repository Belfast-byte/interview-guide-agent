package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.PracticeCoachingContext;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeRecallSource;
import interview.guide.modules.interview.agent.adaptive.memory.episode.PracticeDiagnosticView;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeAssistanceLevel;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeClosureStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PracticeCoachingMemoryAssemblerTest {

  private static final MemoryOwner OWNER = new MemoryOwner(null, "candidate-1");
  private static final TopicKey TOPIC = new TopicKey("redis", "persistence");

  private final PracticeMemoryService memoryService = mock(PracticeMemoryService.class);
  private final EpisodeRecallSource episodeRecall = mock(EpisodeRecallSource.class);
  private final PracticeMemoryOwnerSource ownerSource = mock(PracticeMemoryOwnerSource.class);
  private final PracticeCoachingMemoryAssembler assembler =
      new PracticeCoachingMemoryAssembler(memoryService, episodeRecall, ownerSource);

  @Test
  @DisplayName("正式 Interviewer 不读取 Semantic 或完整 Episode")
  void shouldNotLoadMemoryForEvaluation() {
    PracticeCoachingContext memory = assembler.assemble(request(SessionMode.EVALUATION));

    assertThat(memory).isNull();
    verify(ownerSource, never()).findOwner("session-1");
    verify(episodeRecall, never()).practice("session-1", TOPIC, "Redis 持久化");
  }

  @Test
  @DisplayName("练习 Target 固定后读取当前 Topic 的状态和完整 Episode")
  void shouldLoadMemoryForFixedPracticeTarget() {
    PracticePlanningTopic topic = new PracticePlanningTopic(
        TOPIC,
        new PracticePlanningStatus(
            EvaluatedAbility.WEAK,
            PracticeMastery.ASSISTED,
            TransferStatus.NOT_REEVALUATED
        ),
        List.of()
    );
    PracticeDiagnosticView diagnostic = new PracticeDiagnosticView(
        1,
        1L,
        TOPIC,
        "旧问题",
        "旧回答",
        DepthLevel.L1,
        0.7,
        List.of(),
        List.of(),
        List.of(),
        EpisodeAssistanceLevel.FOLLOW_UP,
        EpisodeClosureStatus.UNRESOLVED,
        0.8
    );
    when(ownerSource.findOwner("session-1")).thenReturn(OWNER);
    when(memoryService.planning(OWNER, new PracticeScope(List.of(TOPIC))))
        .thenReturn(new PracticePlanningMemory(List.of(topic)));
    when(episodeRecall.practice("session-1", TOPIC, "Redis 持久化"))
        .thenReturn(List.of(diagnostic));

    PracticeCoachingContext memory = assembler.assemble(request(SessionMode.PRACTICE));

    assertThat(memory.semantic()).containsEntry("topic", TOPIC);
    assertThat(memory.episodes()).singleElement();
  }

  private PracticeCoachingRequest request(SessionMode mode) {
    return new PracticeCoachingRequest(
        new PracticeMemorySession("session-1", mode),
        TOPIC,
        "Redis 持久化"
    );
  }
}
