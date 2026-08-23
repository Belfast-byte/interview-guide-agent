package interview.guide.modules.interview.agent.adaptive.memory.working;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import java.util.List;
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
        turn(2, TurnProvenance.assessmentGap(1, 10))
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
