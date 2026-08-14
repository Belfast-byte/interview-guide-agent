package interview.guide.modules.interview.agent.adaptive.mcp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "adaptive_mcp_audits")
class AdaptiveMcpAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "credential_id", nullable = false, length = 64)
  private String credentialId;

  @Column(name = "tool_name", nullable = false, length = 80)
  private String toolName;

  @Column(name = "resource_id", length = 64)
  private String resourceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private McpAuditOutcome outcome;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected AdaptiveMcpAuditEntity() {}

  AdaptiveMcpAuditEntity(
      McpTenantPrincipal principal,
      String toolName,
      String resourceId,
      McpAuditOutcome outcome
  ) {
    this.tenantId = principal.tenantId();
    this.credentialId = principal.credentialId();
    this.toolName = toolName;
    this.resourceId = resourceId;
    this.outcome = outcome;
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }
}
