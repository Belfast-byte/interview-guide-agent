package interview.guide.modules.interview.agent.adaptive.algorithm.sandbox;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Stable execution identity derived from the submitted workload. */
public final class SandboxExecutionId {

  private static final String FIELD_SEPARATOR = "\u001f";

  private SandboxExecutionId() {}

  public static String from(CreateSandboxExecution command) {
    String targetId = command.workloadType() == SandboxWorkloadType.ALGORITHM
        ? command.problemId()
        : command.scenarioId();
    String value = String.join(
        FIELD_SEPARATOR,
        command.sessionId(),
        Integer.toString(command.turnIndex()),
        command.workloadType().name(),
        targetId,
        command.language().name(),
        command.codeHash(),
        command.runMode().name()
    );
    return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
  }
}
