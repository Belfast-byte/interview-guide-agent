package interview.guide.modules.interview.agent.adaptive.mcp;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * MCP 调用审计仓储。
 */
interface AdaptiveMcpAuditRepository extends JpaRepository<AdaptiveMcpAuditEntity, Long> {}
