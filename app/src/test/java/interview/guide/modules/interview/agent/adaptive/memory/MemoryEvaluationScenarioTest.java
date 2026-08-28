package interview.guide.modules.interview.agent.adaptive.memory;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EvaluationRecallView;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionIdentity;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionNoveltyDecision;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionNoveltyPolicy;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.EvaluatedAbility;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.EvaluationAggregate;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.EvaluationContribution;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticAggregator;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticSource;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemoryEvaluationScenarioTest {

  private static final TopicKey REDIS_PERSISTENCE =
      new TopicKey("redis", "persistence");
  private static final String OBJECTIVE = "解释 RDB fork 与写时复制的内存影响";

  @Test
  @DisplayName("正式面试命中旧题后只换场景并由本轮证据生成正式贡献")
  void shouldRewriteWithoutChangingPlanOrReusingHistoricalRating() {
    CapabilityTarget target = target();
    QuestionIdentity draft = identity("fork-process", "explain-bgsave");
    EvaluationRecallView oldExposure = oldExposure();
    QuestionNoveltyPolicy novelty = new QuestionNoveltyPolicy();

    QuestionNoveltyDecision decision = novelty.decide(draft, List.of(oldExposure));
    QuestionIdentity rewritten = identity("memory-spike", "diagnose-cow-growth");
    novelty.requireSameEnvelope(draft, rewritten);

    EvaluationContribution current = new EvaluationContribution(
        new SemanticSource(202L, new MemoryOwner(null, "candidate-1"),
            REDIS_PERSISTENCE, LocalDateTime.of(2026, 8, 28, 11, 0)),
        DepthLevel.L2
    );
    EvaluationAggregate aggregate = new SemanticAggregator()
        .evaluation(List.of(current), List.of());

    assertThat(decision.type()).isEqualTo(QuestionNoveltyDecision.Type.REWRITE);
    assertThat(rewritten.topic()).isEqualTo(target.identity().topic());
    assertThat(rewritten.probeDepth()).isEqualTo(target.depth().expected());
    assertThat(current.source().episodeId()).isEqualTo(202L);
    assertThat(aggregate.ability()).isEqualTo(EvaluatedAbility.COMPETENT);
  }

  private CapabilityTarget target() {
    return new CapabilityTarget(
        new CapabilityTarget.Identity(0, "Redis", "持久化", REDIS_PERSISTENCE),
        new CapabilityTarget.Budget(2, 2, 1, 0),
        new CapabilityTarget.Depth(DepthLevel.L2, DepthLevel.L3),
        List.of(new CapabilityTarget.EvidenceObjective(
            OBJECTIVE, CapabilityTarget.EvidenceMethod.CANDIDATE_ANSWER)),
        List.of()
    );
  }

  private QuestionIdentity identity(String scenario, String wording) {
    return new QuestionIdentity(
        REDIS_PERSISTENCE, OBJECTIVE, DepthLevel.L2, "CAMPUS", scenario, wording);
  }

  private EvaluationRecallView oldExposure() {
    return new EvaluationRecallView(
        101L,
        100L,
        "解释 BGSAVE 的 fork 过程",
        "fork-process",
        REDIS_PERSISTENCE,
        OBJECTIVE,
        DepthLevel.L2,
        "CAMPUS",
        0.95,
        "重新验证持续写入时 fork/COW 的内存放大条件"
    );
  }
}
