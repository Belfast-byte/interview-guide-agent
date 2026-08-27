package interview.guide.modules.interview.agent.adaptive.core.memory;

import java.util.List;

/** 一次原子 WorkState 变更。 */
public record WorkStatePatch(
    String patchId,
    String sessionId,
    long baseRevision,
    long resultRevision,
    WorkStatePatchSource sourceType,
    String sourceId,
    List<WorkStateOperation> operations
) {

  public WorkStatePatch {
    operations = List.copyOf(operations);
    if (resultRevision != baseRevision + 1) {
      throw new IllegalArgumentException("Patch resultRevision 必须等于 baseRevision + 1");
    }
    if (operations.isEmpty()) {
      throw new IllegalArgumentException("WorkState Patch 不能为空");
    }
  }
}
