package interview.guide.modules.interview.agent.adaptive.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewApplicationService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.AnalysisJobStatus;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisJob;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisPersistenceService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisProperties;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisSubmissionService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.ProjectDigest;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.trace.CodeTraceService;
import io.modelcontextprotocol.common.McpTransportContext;
import java.time.LocalDateTime;
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

  @Test
  @DisplayName("提交他人租户前缀的仓库 key 时按跨租户 404 拒绝并写审计")
  void shouldRejectRepositoryRefOfAnotherTenantPrefix() {
    McpTenantPrincipal principal = principalWith(McpInterviewScope.CODE_ANALYSIS_SUBMIT);
    when(context.transportContext()).thenReturn(transportContext);
    when(transportContext.get(McpTenantTransportConfiguration.PRINCIPAL_KEY))
        .thenReturn(principal);

    assertThatThrownBy(() -> tools.submitRepository(
        context,
        "session-1",
        "code-analysis/tenant-b/session-1/repo.zip",
        "abc123"
    ))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("code", 404)
        .hasMessage("代码仓库快照不存在");

    verify(submissionService, never()).submit(
        anyString(),
        anyString(),
        anyString(),
        anyString(),
        any(LocalDateTime.class)
    );
    verify(auditService).record(
        principal,
        "code.submit_repo",
        null,
        McpAuditOutcome.NOT_FOUND
    );
  }

  @Test
  @DisplayName("提交平台其他命名空间(简历/候选人源码)的 key 时按跨租户 404 拒绝")
  void shouldRejectRepositoryRefOutsideCodeAnalysisNamespace() {
    McpTenantPrincipal principal = principalWith(McpInterviewScope.CODE_ANALYSIS_SUBMIT);
    when(context.transportContext()).thenReturn(transportContext);
    when(transportContext.get(McpTenantTransportConfiguration.PRINCIPAL_KEY))
        .thenReturn(principal);

    assertThatThrownBy(() -> tools.submitRepository(
        context,
        "session-1",
        "sandbox/sources/session-1/00000000-0000-0000-0000-000000000001.java",
        "abc123"
    ))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("code", 404)
        .hasMessage("代码仓库快照不存在");

    verify(submissionService, never()).submit(
        anyString(),
        anyString(),
        anyString(),
        anyString(),
        any(LocalDateTime.class)
    );
    verify(auditService).record(
        principal,
        "code.submit_repo",
        null,
        McpAuditOutcome.NOT_FOUND
    );
  }

  @Test
  @DisplayName("提交本租户会话前缀内的 key 时放行并投递分析任务")
  void shouldSubmitRepositoryRefOwnedByTenant() {
    McpTenantPrincipal principal = principalWith(McpInterviewScope.CODE_ANALYSIS_SUBMIT);
    CodeAnalysisJob job = new CodeAnalysisJob(
        "job-1",
        "session-1",
        "repo-1",
        AnalysisJobStatus.PENDING,
        null,
        null,
        null,
        null
    );
    when(context.transportContext()).thenReturn(transportContext);
    when(transportContext.get(McpTenantTransportConfiguration.PRINCIPAL_KEY))
        .thenReturn(principal);
    when(submissionService.submit(
        eq("session-1"),
        eq("tenant-a"),
        eq("code-analysis/tenant-a/session-1/repo.zip"),
        eq("abc123"),
        any(LocalDateTime.class)
    )).thenReturn(job);

    assertThat(tools.submitRepository(
        context,
        "session-1",
        "code-analysis/tenant-a/session-1/repo.zip",
        "abc123"
    )).isSameAs(job);

    verify(interviewService).getForTenant("tenant-a", "session-1");
    verify(auditService).record(
        principal,
        "code.submit_repo",
        "job-1",
        McpAuditOutcome.SUCCEEDED
    );
  }

  private McpTenantPrincipal principalWith(McpInterviewScope scope) {
    return new McpTenantPrincipal("tenant-a", "credential-a", Set.of(scope));
  }
}
