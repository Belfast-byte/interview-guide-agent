package interview.guide.modules.interview.agent.adaptive.core.context;

import java.util.Objects;

/**
 * 候选人长期记忆的租户隔离所有者。
 */
public record MemoryOwner(String tenantId, String candidateId) {

  public MemoryOwner {
    tenantId = tenantId == null ? null : requireText(tenantId, "tenantId");
    candidateId = requireText(candidateId, "candidateId");
  }

  private static String requireText(String value, String field) {
    Objects.requireNonNull(value, field + " 不能为空");
    String normalized = value.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(field + " 不能为空");
    }
    return normalized;
  }
}
