package interview.guide.modules.interview.agent.adaptive.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewApplicationService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnalysisPersistenceService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnalysisProperties;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnalysisSubmissionService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.ProjectDigest;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeTraceService;
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

@ExtendWith(MockitoExtension.class)
class CodeAnalysisMcpToolsTest {

  @Mock
  private AdaptiveInterviewApplicationService interviewService;

  @Mock
  private CodeAnalysisSubmissionService submissionService;

  @Mock
  private CodeAnalysisPersistenceService persistenceService;

  @Mock
  private AdaptiveMcpAuditService auditService;

  @Mock
  private CodeTraceService traceService;

  @Mock
  private McpSyncRequestContext context;

  @Mock
  private McpTransportContext transportContext;

  private CodeAnalysisMcpTools tools;

  @BeforeEach
  void setUp() {
    tools = new CodeAnalysisMcpTools(
        interviewService,
        submissionService,
        persistenceService,
        new CodeAnalysisProperties(),
        auditService,
        traceService
    );
  }

  @Test
  @DisplayName("MCP 层暴露提交、三类产物查询和受限代码追踪五个工具")
  void shouldExposeThinArtifactTools() {
    assertThat(new SyncMcpToolProvider(List.of(tools)).getToolSpecifications())
        .extracting(specification -> specification.tool().name())
        .containsExactlyInAnyOrder(
            "code.submit_repo",
            "code.get_digest",
            "code.get_claim_verifications",
            "code.get_scenarios",
            "code.trace"
        );
  }

  @Test
  @DisplayName("没有代码分析读取 scope 时在访问存储前拒绝")
  void shouldRejectMissingReadScope() {
    McpTenantPrincipal principal = new McpTenantPrincipal(
        "tenant-a",
        "credential-a",
        Set.of(McpInterviewScope.INTERVIEW_READ)
    );
    when(context.transportContext()).thenReturn(transportContext);
    when(transportContext.get(McpTenantTransportConfiguration.PRINCIPAL_KEY))
        .thenReturn(principal);

    assertThatThrownBy(() -> tools.getDigest(context, "session-1", "job-1"))
        .hasFieldOrPropertyWithValue("code", 403);

    verify(persistenceService, never()).getDigest("session-1", "job-1");
    verify(auditService).record(
        principal,
        "code.get_digest",
        null,
        McpAuditOutcome.FORBIDDEN
    );
  }

  @Test
  @DisplayName("查询产物前按凭证租户校验会话归属")
  void shouldAuthorizeSessionBeforeReadingDigest() {
    McpTenantPrincipal principal = new McpTenantPrincipal(
        "tenant-a",
        "credential-a",
        Set.of(McpInterviewScope.CODE_ANALYSIS_READ)
    );
    ProjectDigest digest = new ProjectDigest(
        "digest-1",
        "abc123",
        List.of("Java"),
        List.of(),
        List.of(),
        List.of()
    );
    when(context.transportContext()).thenReturn(transportContext);
    when(transportContext.get(McpTenantTransportConfiguration.PRINCIPAL_KEY))
        .thenReturn(principal);
    when(persistenceService.getDigest("session-1", "job-1")).thenReturn(digest);

    assertThat(tools.getDigest(context, "session-1", "job-1")).isSameAs(digest);

    verify(interviewService).getForTenant("tenant-a", "session-1");
    verify(auditService).record(
        principal,
        "code.get_digest",
        "job-1",
        McpAuditOutcome.SUCCEEDED
    );
  }
}
