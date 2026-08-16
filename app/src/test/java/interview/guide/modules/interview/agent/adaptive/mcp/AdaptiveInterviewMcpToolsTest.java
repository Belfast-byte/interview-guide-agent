package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewApplicationService;
import interview.guide.modules.interview.agent.adaptive.assessment.report.AssessmentReportService;
import interview.guide.modules.interview.agent.adaptive.assessment.report.EnterpriseAssessmentReport;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.annotation.provider.tool.SyncMcpToolProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdaptiveInterviewMcpToolsTest {

  @Mock
  private AdaptiveInterviewApplicationService applicationService;

  @Mock
  private AdaptiveMcpAuditService auditService;

  @Mock
  private AssessmentReportService reportService;

  @Mock
  private McpSyncRequestContext context;

  @Mock
  private McpTransportContext transportContext;

  private AdaptiveInterviewMcpTools tools;

  @BeforeEach
  void setUp() {
    tools = new AdaptiveInterviewMcpTools(
        applicationService,
        reportService,
        auditService
    );
  }

  @Test
  @DisplayName("Spring AI 扫描出评估阶段启用的四个 MCP 工具")
  void shouldExposeDocumentedTools() {
    assertThat(new SyncMcpToolProvider(List.of(tools)).getToolSpecifications())
        .extracting(specification -> specification.tool().name())
        .containsExactlyInAnyOrder(
            "interview.create",
            "interview.get_status",
            "interview.list_dimensions",
            "interview.get_report"
        );
  }

  @Test
  @DisplayName("缺少读取 scope 时拒绝调用并写审计")
  void shouldRejectMissingScope() {
    when(context.transportContext()).thenReturn(transportContext);
    McpTenantPrincipal principal = principal(Set.of(McpInterviewScope.INTERVIEW_CREATE));
    when(transportContext.get(McpTenantTransportConfiguration.PRINCIPAL_KEY))
        .thenReturn(principal);

    assertThatThrownBy(() -> tools.getStatus(context, "session-b"))
        .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());

    verify(auditService).record(
        principal,
        "interview.get_status",
        null,
        McpAuditOutcome.FORBIDDEN
    );
    verify(applicationService, never()).getForTenant("tenant-a", "session-b");
  }

  @Test
  @DisplayName("跨租户 sessionId 返回不存在并写审计")
  void shouldReturnNotFoundForAnotherTenantSession() {
    when(context.transportContext()).thenReturn(transportContext);
    McpTenantPrincipal principal = principal(Set.of(McpInterviewScope.INTERVIEW_READ));
    when(transportContext.get(McpTenantTransportConfiguration.PRINCIPAL_KEY))
        .thenReturn(principal);
    when(applicationService.getForTenant("tenant-a", "session-b"))
        .thenThrow(new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));

    assertThatThrownBy(() -> tools.getStatus(context, "session-b"))
        .hasFieldOrPropertyWithValue(
            "code",
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND.getCode()
        );

    verify(auditService).record(
        principal,
        "interview.get_status",
        "session-b",
        McpAuditOutcome.NOT_FOUND
    );
  }

  @Test
  @DisplayName("报告工具只按凭证租户读取并记录成功审计")
  void shouldGetReportForCredentialTenant() {
    when(context.transportContext()).thenReturn(transportContext);
    McpTenantPrincipal principal = principal(Set.of(McpInterviewScope.INTERVIEW_READ));
    when(transportContext.get(McpTenantTransportConfiguration.PRINCIPAL_KEY))
        .thenReturn(principal);
    EnterpriseAssessmentReport report = new EnterpriseAssessmentReport(
        "session-a",
        "candidate-a",
        List.of(),
        List.of(),
        "AI 初筛建议，不构成录用决定"
    );
    when(reportService.enterpriseReport("tenant-a", "session-a"))
        .thenReturn(report);

    assertThat(tools.getReport(context, "session-a")).isSameAs(report);

    verify(auditService).record(
        principal,
        "interview.get_report",
        "session-a",
        McpAuditOutcome.SUCCEEDED
    );
  }

  private McpTenantPrincipal principal(Set<McpInterviewScope> scopes) {
    return new McpTenantPrincipal("tenant-a", "credential-a", scopes);
  }
}
