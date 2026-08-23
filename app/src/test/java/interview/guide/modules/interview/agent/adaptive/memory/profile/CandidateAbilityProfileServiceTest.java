package interview.guide.modules.interview.agent.adaptive.memory.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityCounter;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileRevisionReason;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileSnapshot;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticAbility;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CandidateAbilityProfileServiceTest {

  @Test
  @DisplayName("候选人画像服务只通过业务端口读取个人轨迹")
  void shouldReadCandidateTrajectoryThroughPort() {
    CandidateAbilityProfileSource source = mock(CandidateAbilityProfileSource.class);
    MemoryOwner owner = new MemoryOwner(null, "candidate-1");
    AbilityProfileSnapshot profile = profile();
    when(source.trajectory(owner)).thenReturn(List.of(profile));

    List<AbilityProfileSnapshot> result =
        new CandidateAbilityProfileService(source).trajectory(owner.candidateId());

    assertThat(result).containsExactly(profile);
    verify(source).trajectory(owner);
  }

  private AbilityProfileSnapshot profile() {
    return new AbilityProfileSnapshot(
        1L,
        new MemoryOwner(null, "candidate-1"),
        new TopicKey("java", "concurrency"),
        SemanticAbility.COMPETENT,
        new AbilityCounter(0, 0, 1, 0, 0),
        "session-1",
        AbilityProfileRevisionReason.SESSION_COMPLETED,
        null,
        LocalDateTime.of(2026, 8, 23, 10, 0)
    );
  }
}
