package interview.guide.modules.interview.agent.adaptive.mcp;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(AdaptiveMcpAuditService.class)
class AdaptiveMcpAuditServiceTest {

  @Autowired
  private AdaptiveMcpAuditService auditService;

  @Autowired
  private AdaptiveMcpAuditRepository repository;

  @Test
  @DisplayName("MCP 调用只审计租户、凭证、工具、资源和结果")
  void shouldPersistGovernanceAudit() {
    McpTenantPrincipal principal = new McpTenantPrincipal(
        "tenant-a",
        "credential-a",
        Set.of(McpInterviewScope.INTERVIEW_READ)
    );

    auditService.record(
        principal,
        "interview.get_status",
        "session-1",
        McpAuditOutcome.SUCCEEDED
    );

    assertThat(repository.count()).isOne();
  }

  @Test
  @DisplayName("MCP 审计 schema 不保存凭证或面试敏感原文")
  void shouldExcludeSecretsAndInterviewTextFromAuditSchema() {
    assertThat(Arrays.stream(AdaptiveMcpAuditEntity.class.getDeclaredFields())
        .map(Field::getName)
        .map(name -> name.toLowerCase(Locale.ROOT)))
        .noneMatch(name -> name.contains("apikey")
            || name.contains("authorization")
            || name.contains("resume")
            || name.equals("jd")
            || name.contains("answer"));
  }
}
