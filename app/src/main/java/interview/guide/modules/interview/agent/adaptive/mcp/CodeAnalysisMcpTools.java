package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewApplicationService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.ClaimVerification;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnalysisJob;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnalysisPersistenceService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnalysisProperties;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnalysisSubmissionService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeTraceResult;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeTraceService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.ProjectDigest;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.ScenarioCard;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 代码分析 MCP 工具集合。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.interview.adaptive-agent",
    name = "enabled",
    havingValue = "true"
)
public class CodeAnalysisMcpTools {

  private static final String SUBMIT_TOOL = "code.submit_repo";
  private static final String DIGEST_TOOL = "code.get_digest";
  private static final String CLAIMS_TOOL = "code.get_claim_verifications";
  private static final String SCENARIOS_TOOL = "code.get_scenarios";
  private static final String TRACE_TOOL = "code.trace";

  private final AdaptiveInterviewApplicationService interviewService;
  private final CodeAnalysisSubmissionService submissionService;
  private final CodeAnalysisPersistenceService persistenceService;
  private final CodeAnalysisProperties properties;
  private final AdaptiveMcpAuditService auditService;
  private final CodeTraceService traceService;

  @McpTool(
      name = SUBMIT_TOOL,
      description = "Submit an interview-owned repository snapshot for asynchronous analysis"
  )
  public CodeAnalysisJob submitRepository(
      McpSyncRequestContext context,
      @McpToolParam(description = "Interview session identifier") String sessionId,
      @McpToolParam(description = "S3 key or authorized git URL") String repositoryRef,
      @McpToolParam(description = "Repository snapshot commit hash") String commitHash
  ) {
    validateSubmitInput(sessionId, repositoryRef, commitHash);
    McpTenantPrincipal principal = requireScope(
        context,
        SUBMIT_TOOL,
        McpInterviewScope.CODE_ANALYSIS_SUBMIT
    );
    interviewService.getForTenant(principal.tenantId(), sessionId);
    CodeAnalysisJob job = submissionService.submit(
        sessionId,
        principal.tenantId(),
        repositoryRef,
        commitHash,
        LocalDateTime.now().plus(properties.getRetention())
    );
    auditService.record(principal, SUBMIT_TOOL, job.id(), McpAuditOutcome.SUCCEEDED);
    return job;
  }

  @McpTool(name = DIGEST_TOOL, description = "Get the project digest for an analysis job")
  public ProjectDigest getDigest(
      McpSyncRequestContext context,
      @McpToolParam(description = "Interview session identifier") String sessionId,
      @McpToolParam(description = "Code analysis job identifier") String jobId
  ) {
    McpTenantPrincipal principal = authorizeRead(context, DIGEST_TOOL, sessionId);
    ProjectDigest digest = persistenceService.getDigest(sessionId, jobId);
    auditService.record(principal, DIGEST_TOOL, jobId, McpAuditOutcome.SUCCEEDED);
    return digest;
  }

  @McpTool(
      name = CLAIMS_TOOL,
      description = "List structured claim verifications for an analysis job"
  )
  public List<ClaimVerification> getClaimVerifications(
      McpSyncRequestContext context,
      @McpToolParam(description = "Interview session identifier") String sessionId,
      @McpToolParam(description = "Code analysis job identifier") String jobId
  ) {
    McpTenantPrincipal principal = authorizeRead(context, CLAIMS_TOOL, sessionId);
    List<ClaimVerification> claims = persistenceService.getClaimVerifications(sessionId, jobId);
    auditService.record(principal, CLAIMS_TOOL, jobId, McpAuditOutcome.SUCCEEDED);
    return claims;
  }

  @McpTool(name = SCENARIOS_TOOL, description = "List scenario cards for an analysis job")
  public List<ScenarioCard> getScenarios(
      McpSyncRequestContext context,
      @McpToolParam(description = "Interview session identifier") String sessionId,
      @McpToolParam(description = "Code analysis job identifier") String jobId
  ) {
    McpTenantPrincipal principal = authorizeRead(context, SCENARIOS_TOOL, sessionId);
    List<ScenarioCard> scenarios = persistenceService.getScenarios(sessionId, jobId);
    auditService.record(principal, SCENARIOS_TOOL, jobId, McpAuditOutcome.SUCCEEDED);
    return scenarios;
  }

  @McpTool(name = TRACE_TOOL, description = "Locate a symbol or exact text in the repository")
  public CodeTraceResult trace(
      McpSyncRequestContext context,
      @McpToolParam(description = "Interview session identifier") String sessionId,
      @McpToolParam(description = "Symbol or exact text to locate") String query
  ) {
    if (query == null || query.isBlank() || query.length() > 120) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "代码追踪查询无效");
    }
    McpTenantPrincipal principal = requireScope(
        context,
        TRACE_TOOL,
        McpInterviewScope.CODE_ANALYSIS_TRACE
    );
    interviewService.getForTenant(principal.tenantId(), sessionId);
    CodeTraceResult result = traceService.trace(sessionId, query);
    auditService.record(principal, TRACE_TOOL, sessionId, McpAuditOutcome.SUCCEEDED);
    return result;
  }

  private McpTenantPrincipal authorizeRead(
      McpSyncRequestContext context,
      String toolName,
      String sessionId
  ) {
    McpTenantPrincipal principal = requireScope(
        context,
        toolName,
        McpInterviewScope.CODE_ANALYSIS_READ
    );
    interviewService.getForTenant(principal.tenantId(), sessionId);
    return principal;
  }

  private McpTenantPrincipal requireScope(
      McpSyncRequestContext context,
      String toolName,
      McpInterviewScope scope
  ) {
    McpTenantPrincipal principal = (McpTenantPrincipal) context.transportContext()
        .get(McpTenantTransportConfiguration.PRINCIPAL_KEY);
    if (!principal.allows(scope)) {
      auditService.record(principal, toolName, null, McpAuditOutcome.FORBIDDEN);
      throw new BusinessException(ErrorCode.FORBIDDEN, "MCP scope 不足");
    }
    return principal;
  }

  private void validateSubmitInput(String sessionId, String repositoryRef, String commitHash) {
    if (sessionId == null || sessionId.isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "面试会话标识不能为空");
    }
    if (repositoryRef == null || repositoryRef.isBlank() || repositoryRef.length() > 512) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "仓库引用无效");
    }
    if (commitHash == null || commitHash.isBlank() || commitHash.length() > 64) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "commitHash 无效");
    }
  }
}
