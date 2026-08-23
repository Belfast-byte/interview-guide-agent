package interview.guide.modules.interview.agent.adaptive.memory.working;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemorySnapshot;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkingMemoryAssemblerTest {

  private static final TopicKey TOPIC = new TopicKey("java-backend", "REDIS");
  private final ContextAssembler assembler = new ContextAssembler();

  @Test
  @DisplayName("计划问题是 follow-up 深度为零的根问题")
  void shouldBuildPlannedRootSnapshot() {
    WorkingMemorySnapshot snapshot = assembler.workingMemory(new WorkingMemoryInput(
        "session-1",
        3,
        TOPIC,
        null,
        TurnTriggerType.PLANNED,
        List.of(),
        List.of(turn(1, TurnProvenance.initial()))
    ));

    assertThat(snapshot.followUpDepth()).isZero();
    assertThat(snapshot.selectedGap()).isNull();
  }

  @Test
  @DisplayName("评估追问选择首个 gap 并从父链计算深度")
  void shouldBuildAssessmentFollowUpSnapshot() {
    ProbeGap selected = new ProbeGap("缓存", "未说明失败边界");
    List<AdaptiveInterviewTurn> history = List.of(
        turn(1, TurnProvenance.initial()),
        turn(2, TurnProvenance.assessmentGap(1, 10, 11))
    );

    WorkingMemorySnapshot snapshot = assembler.workingMemory(new WorkingMemoryInput(
        "session-1",
        3,
        TOPIC,
        2,
        TurnTriggerType.ASSESSMENT_GAP,
        List.of(selected, new ProbeGap("版本", "未说明推进")),
        history
    ));

    assertThat(snapshot.selectedGap()).isEqualTo(selected);
    assertThat(snapshot.followUpDepth()).isEqualTo(2);
  }

  @Test
  @DisplayName("工具结果追问沿父链计算深度且不携带 gap")
  void shouldBuildToolResultSnapshot() {
    WorkingMemorySnapshot snapshot = assembler.workingMemory(new WorkingMemoryInput(
        "session-1",
        2,
        TOPIC,
        1,
        TurnTriggerType.TOOL_RESULT,
        List.of(),
        List.of(turn(1, TurnProvenance.initial()))
    ));

    assertThat(snapshot.followUpDepth()).isEqualTo(1);
    assertThat(snapshot.selectedGap()).isNull();
  }

  @Test
  @DisplayName("未使用的持久化 gap 可恢复且保留原 Assessment 来源")
  void shouldRecoverUnusedPersistedGap() {
    ProbeGap recovered = new ProbeGap("缓存", "未说明恢复边界");
    ProbeGapCandidate candidate = new ProbeGapCandidate(
        11, 10L, 1, TOPIC, 1, recovered
    );

    WorkingMemorySelection selection = assembler.nextQuestionWorkingMemory(
        new NextQuestionWorkingMemoryInput(
            "session-1",
            2,
            TOPIC,
            List.of(),
            List.of(candidate),
            List.of(turn(1, TurnProvenance.initial()))
        )
    );

    assertThat(selection.snapshot().selectedGap()).isEqualTo(recovered);
    assertThat(selection.snapshot().triggerType()).isEqualTo(TurnTriggerType.ASSESSMENT_GAP);
    assertThat(selection.provenance().resolve(20, Map.of()).trigger().sourceAssessmentId())
        .isEqualTo(10L);
    assertThat(selection.provenance().resolve(20, Map.of()).trigger().sourceProbeGapId())
        .isEqualTo(11L);
  }

  @Test
  @DisplayName("已使用 gap 被过滤并由当前评估 gap 形成多级追问")
  void shouldSkipUsedGapAndBuildNestedCurrentGap() {
    ProbeGap used = new ProbeGap("旧锚点", "旧缺口");
    ProbeGap current = new ProbeGap("当前锚点", "当前缺口");
    List<AdaptiveInterviewTurn> history = List.of(
        turn(1, TurnProvenance.initial()),
        turn(2, TurnProvenance.assessmentGap(1, 10, 11))
    );

    WorkingMemorySelection selection = assembler.nextQuestionWorkingMemory(
        new NextQuestionWorkingMemoryInput(
            "session-1",
            3,
            TOPIC,
            List.of(current),
            List.of(new ProbeGapCandidate(11, 10L, 1, TOPIC, 1, used)),
            history
        )
    );

    assertThat(selection.snapshot().selectedGap()).isEqualTo(current);
    assertThat(selection.snapshot().followUpDepth()).isEqualTo(2);
    assertThat(selection.provenance().resolve(20, Map.of(1, 21L))
        .trigger().sourceAssessmentId())
        .isEqualTo(20L);
    assertThat(selection.provenance().resolve(20, Map.of(1, 21L))
        .trigger().sourceProbeGapId())
        .isEqualTo(21L);
  }

  @Test
  @DisplayName("父轮次不在当前会话历史时明确失败")
  void shouldRejectMissingParent() {
    assertThatThrownBy(() -> assembler.workingMemory(new WorkingMemoryInput(
        "session-1",
        2,
        TOPIC,
        1,
        TurnTriggerType.TOOL_RESULT,
        List.of(),
        List.of()
    ))).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("父轮次");
  }

  private AdaptiveInterviewTurn turn(int turnIndex, TurnProvenance provenance) {
    RespondAction question = RespondAction.ask("问题" + turnIndex, "原因");
    return new AdaptiveInterviewTurn(
        turnIndex,
        0,
        question.content(),
        question.reason(),
        "回答",
        null,
        null,
        null,
        provenance
    );
  }

}
