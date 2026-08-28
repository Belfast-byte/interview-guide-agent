package interview.guide.modules.interview.agent.adaptive.memory;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.PracticeCoachingContext;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeAssistanceLevel;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeClosureStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeRecallSource;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EvaluationRecallView;
import interview.guide.modules.interview.agent.adaptive.memory.episode.PracticeDiagnosticView;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeAggregate;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeCoachingMemoryAssembler;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeCoachingRequest;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeContribution;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMastery;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMemoryService;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMemorySession;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeOutcome;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticePlanningMemory;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeResult;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeSemanticState;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeStatistics;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.LatestPractice;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticAggregator;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticSource;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticStateKey;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticTrack;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.TransferAssessment;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.TransferStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemoryPracticeScenarioTest {

  private static final MemoryOwner OWNER = new MemoryOwner(null, "candidate-1");
  private static final TopicKey REDIS_PERSISTENCE = new TopicKey("redis", "persistence");
  private static final TopicKey REDIS_CLUSTER = new TopicKey("redis", "cluster");

  @Test
  @DisplayName("练习只在 scope 内选择弱项并以完整诊断追问后更新练习轨")
  void shouldConsumeScopedDiagnosticsAndRecordAssistedMastery() {
    PracticeMemoryService memory = memoryService();
    PracticePlanningMemory planning = memory.planning(
        OWNER, new PracticeScope(List.of(REDIS_PERSISTENCE)));
    PracticeCoachingContext coaching = assembler(memory).assemble(new PracticeCoachingRequest(
        new PracticeMemorySession("practice-1", SessionMode.PRACTICE),
        REDIS_PERSISTENCE,
        "fork COW"
    ));

    PracticeContribution retest = new PracticeContribution(
        new SemanticSource(12L, OWNER, REDIS_PERSISTENCE,
            LocalDateTime.of(2026, 8, 28, 12, 0)),
        new PracticeResult(
            PracticeOutcome.COMPLETED, EpisodeAssistanceLevel.HINT, DepthLevel.L2)
    );
    PracticeAggregate updated = new SemanticAggregator()
        .practice(List.of(retest), List.of(), List.of());

    assertThat(planning.topics()).extracting(topic -> topic.topic())
        .containsExactly(REDIS_PERSISTENCE);
    assertThat(coaching.episodes().getFirst())
        .containsEntry("answer", "只说 fork，没有解释父子进程写时复制")
        .containsEntry("assistanceLevel", EpisodeAssistanceLevel.HINT);
    assertThat(updated.mastery()).isEqualTo(PracticeMastery.ASSISTED);
    assertThat(updated.statistics().completed(EpisodeAssistanceLevel.HINT)).isEqualTo(1);
    assertThat(updated.transfer().status()).isEqualTo(TransferStatus.NOT_REEVALUATED);
  }

  private PracticeMemoryService memoryService() {
    PracticeSemanticState selected = state(REDIS_PERSISTENCE, PracticeMastery.UNRESOLVED);
    PracticeSemanticState outsideScope = state(REDIS_CLUSTER, PracticeMastery.INDEPENDENT);
    return new PracticeMemoryService(owner -> List.of(selected, outsideScope));
  }

  private PracticeCoachingMemoryAssembler assembler(PracticeMemoryService memory) {
    return new PracticeCoachingMemoryAssembler(
        memory,
        new ScenarioEpisodeRecallSource(),
        sessionId -> OWNER
    );
  }

  private PracticeSemanticState state(TopicKey topic, PracticeMastery mastery) {
    LocalDateTime time = LocalDateTime.of(2026, 8, 27, 12, 0);
    PracticeResult result = new PracticeResult(
        mastery == PracticeMastery.UNRESOLVED
            ? PracticeOutcome.UNRESOLVED
            : PracticeOutcome.COMPLETED,
        EpisodeAssistanceLevel.NONE,
        DepthLevel.L2
    );
    return new PracticeSemanticState(
        new SemanticStateKey(OWNER, topic, SemanticTrack.PRACTICE_MASTERY),
        1L,
        new PracticeStatistics(Map.of(), mastery == PracticeMastery.UNRESOLVED ? 1 : 0,
            new LatestPractice(10L, result, time)),
        mastery,
        List.of(),
        new TransferAssessment(TransferStatus.NOT_REEVALUATED, null),
        time
    );
  }

  private static final class ScenarioEpisodeRecallSource implements EpisodeRecallSource {

    @Override
    public List<EvaluationRecallView> evaluation(
        String sessionId,
        TopicKey topic,
        String question
    ) {
      return List.of();
    }

    @Override
    public List<PracticeDiagnosticView> practice(
        String sessionId,
        TopicKey topic,
        String question
    ) {
      return List.of(new PracticeDiagnosticView(
          11L, 10L, REDIS_PERSISTENCE,
          "BGSAVE 时为什么内存突增？",
          "只说 fork，没有解释父子进程写时复制",
          DepthLevel.L1,
          0.9,
          List.of("说出了 fork"),
          List.of(new ProbeGap("fork", "解释 COW 页复制条件")),
          List.of(),
          EpisodeAssistanceLevel.HINT,
          EpisodeClosureStatus.UNRESOLVED,
          0.92
      ));
    }
  }
}
