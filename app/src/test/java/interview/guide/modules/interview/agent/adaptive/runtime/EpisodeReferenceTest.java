package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.QuestionType;

import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemoryValidator;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.WorkingMemorySnapshotReader;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EpisodeReferenceTest {
  @Test
  void snapshotRetainsEpisodeAndCurrentGapWithoutRecoveringOldToolCalls() {
    var repository = mock(AdaptiveAgentTurnRepository.class);
    var entity = mock(AdaptiveAgentTurnEntity.class);
    var memory = new WorkingMemory(1, new WorkingMemory.Focus("target-0", 7L, List.of()),
        new WorkingMemory.Deliberation(List.of(), "换场景验证底层机制", List.of("episode:9", "tool-0-0")));
    when(entity.workingMemory()).thenReturn(memory);
    when(repository.findFirstBySessionIdAndWorkingMemoryIsNotNullOrderByTurnIndexDesc("current"))
        .thenReturn(Optional.of(entity));

    var restored = new WorkingMemorySnapshotReader(repository).latest("current");

    assertThat(restored.focus()).isEqualTo(memory.focus());
    assertThat(restored.deliberation().nextProbeIntent()).isEqualTo("换场景验证底层机制");
    assertThat(restored.deliberation().adoptedObservationRefs()).containsExactly("episode:9");
  }

  @Test
  void nextQuestionCanReuseCommittedEpisodeButCannotInventASource() {
    var validator = new AgentDecisionValidator(new WorkingMemoryValidator());
    var context = mock(AgentContext.class, RETURNS_DEEP_STUBS);
    var target = mock(CoverageView.TargetCoverage.class);
    when(target.targetId()).thenReturn("target-0");
    when(context.facts().coverage().targets()).thenReturn(List.of(target));
    var memory = WorkingMemory.empty().withEpisodeReferences(List.of("episode:9"));
    when(context.workingMemory()).thenReturn(memory);

    assertThat(validator.validateAction(decision(memory, "episode:9"), context, List.of())).isEmpty();
    assertThat(validator.validateAction(decision(memory, "episode:other"), context, List.of())).isPresent();
  }

  private AgentDecision decision(WorkingMemory memory, String reference) {
    return new AgentDecision(memory, new AgentDecision.Ask("target-0", null,
        new AgentDecision.QuestionDraft("换库存场景说明锁竞争", "验证当前不足", List.of(reference), QuestionType.TEXT, null, null)));
  }
}
