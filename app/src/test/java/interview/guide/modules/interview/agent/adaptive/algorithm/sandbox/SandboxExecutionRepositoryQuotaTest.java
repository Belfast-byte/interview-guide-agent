package interview.guide.modules.interview.agent.adaptive.algorithm.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(showSql = false, properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SandboxExecutionRepositoryQuotaTest {

  @Autowired
  private SandboxExecutionRepository executionRepository;

  @Test
  @DisplayName("配额计数排除 superseded、IE 与排队超时，只统计有效执行")
  void shouldCountOnlyQuotaConsumingExecutions() {
    persist("queued", SandboxWorkloadType.ALGORITHM, entity -> {
    });
    persist("running", SandboxWorkloadType.ALGORITHM, SandboxExecutionEntity::markRunning);
    persist("accepted", SandboxWorkloadType.ALGORITHM, this::toAccepted);
    persist("infra-failure", SandboxWorkloadType.ALGORITHM, this::toInfraFailure);
    persist("timeout-degraded", SandboxWorkloadType.ALGORITHM, SandboxExecutionEntity::markQueuedTimeout);
    persist("superseded", SandboxWorkloadType.ALGORITHM, this::toAcceptedThenSuperseded);
    persist("patch-accepted", SandboxWorkloadType.PATCH, this::toAccepted);

    assertThat(executionRepository.countQuotaConsumingBySessionId(
        "quota-session",
        List.of(SandboxExecutionStatus.PENDING, SandboxExecutionStatus.RUNNING),
        SandboxExecutionStatus.DONE,
        SandboxVerdict.IE
    )).isEqualTo(4);
    assertThat(executionRepository.countQuotaConsumingBySessionIdAndWorkloadType(
        "quota-session",
        SandboxWorkloadType.PATCH,
        List.of(SandboxExecutionStatus.PENDING, SandboxExecutionStatus.RUNNING),
        SandboxExecutionStatus.DONE,
        SandboxVerdict.IE
    )).isEqualTo(1);
  }

  private void persist(
      String id,
      SandboxWorkloadType workloadType,
      Consumer<SandboxExecutionEntity> transition
  ) {
    SandboxExecutionEntity entity = new SandboxExecutionEntity(
        id,
        new CreateSandboxExecution(
            "quota-session",
            1,
            workloadType,
            workloadType == SandboxWorkloadType.PATCH ? "scenario-1" : "two-sum",
            workloadType == SandboxWorkloadType.PATCH ? "scenario-1" : null,
            null,
            null,
            SandboxLanguage.JAVA,
            "sandbox/source/" + id + ".java",
            "a".repeat(64),
            SandboxRunMode.FULL
        ),
        9L,
        1
    );
    transition.accept(entity);
    executionRepository.saveAndFlush(entity);
  }

  private void toAccepted(SandboxExecutionEntity entity) {
    entity.markRunning();
    entity.apply(new SandboxExecutionResult(
        SandboxVerdict.AC, 3, 3, 100, 32_768, null, List.of(), null
    ));
  }

  private void toInfraFailure(SandboxExecutionEntity entity) {
    entity.markRunning();
    entity.apply(new SandboxExecutionResult(
        SandboxVerdict.IE, 0, 0, 0, 0, null, List.of(), null
    ));
  }

  private void toAcceptedThenSuperseded(SandboxExecutionEntity entity) {
    toAccepted(entity);
    entity.supersedeWith("new-execution");
  }
}
