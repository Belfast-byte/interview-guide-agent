package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewApplicationService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.claim.ClaimVerification;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisJob;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisPersistenceService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisProperties;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisSubmissionService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.CodeAnalysisRepositoryKeyPolicy;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.ProjectDigest;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario.ScenarioCard;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.trace.CodeTraceResult;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.trace.CodeTraceService;
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
      @McpToolParam(description = "平台生成的仓库快照 S3 key，须位于 code-analysis/{tenantId}/{sessionId}/ 前缀下")
          String repositoryRef,
      @McpToolParam(description = "Repository snapshot commit hash") String commitHash
  ) {
    validateSubmitInput(sessionId, repositoryRef, commitHash);
    McpTenantPrincipal principal = requireScope(
        context,
        SUBMIT_TOOL,
        McpInterviewScope.CODE_ANALYSIS_SUBMIT
    );
    interviewService.getForTenant(principal.tenantId(), sessionId);
    requireOwnedRepositoryRef(principal, sessionId, repositoryRef);
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
    CodeTraceResult result = traceService.trace(principal.tenantId(), sessionId, query);
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
    return McpScopeGuard.requireScope(context, toolName, scope, auditService);
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

  /**
   * 强制仓库快照 key 落在当前租户会话前缀下，防止跨租户读取单 bucket 内的其他对象。
   */
  private void requireOwnedRepositoryRef(
      McpTenantPrincipal principal,
      String sessionId,
      String repositoryRef
  ) {
    if (!CodeAnalysisRepositoryKeyPolicy.isOwned(
        repositoryRef,
        principal.tenantId(),
        sessionId
    )) {
      auditService.record(principal, SUBMIT_TOOL, null, McpAuditOutcome.NOT_FOUND);
      throw new BusinessException(ErrorCode.NOT_FOUND, "代码仓库快照不存在");
    }
  }
}
