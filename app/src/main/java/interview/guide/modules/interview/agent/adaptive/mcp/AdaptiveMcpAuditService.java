package interview.guide.modules.interview.agent.adaptive.mcp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AdaptiveMcpAuditService {

  private final AdaptiveMcpAuditRepository repository;

  @Transactional
  public void record(
      McpTenantPrincipal principal,
      String toolName,
      String resourceId,
      McpAuditOutcome outcome
  ) {
    repository.save(new AdaptiveMcpAuditEntity(
        principal,
        toolName,
        resourceId,
        outcome
    ));
  }
}
