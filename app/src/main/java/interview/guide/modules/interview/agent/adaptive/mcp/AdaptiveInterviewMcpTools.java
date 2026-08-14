package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewApplicationService;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.interview.adaptive-agent",
    name = "enabled",
    havingValue = "true"
)
public class AdaptiveInterviewMcpTools {

  private static final String CREATE_TOOL = "interview.create";
  private static final String STATUS_TOOL = "interview.get_status";
  private static final String DIMENSIONS_TOOL = "interview.list_dimensions";

  private final AdaptiveInterviewApplicationService applicationService;
  private final AdaptiveMcpAuditService auditService;

  @McpTool(
      name = CREATE_TOOL,
      description = "Create a tenant-scoped adaptive text interview"
  )
  public McpInterviewStatusResponse create(
      McpSyncRequestContext context,
      @McpToolParam(description = "Stable candidate identifier") String candidateId,
      @McpToolParam(description = "Job description") String jd,
      @McpToolParam(description = "Candidate resume") String resume,
      @McpToolParam(required = false, description = "Configured LLM provider")
      String llmProvider
  ) {
    validateCreateInput(candidateId, jd, resume, llmProvider);
    McpTenantPrincipal principal = requireScope(
        context,
        CREATE_TOOL,
        McpInterviewScope.INTERVIEW_CREATE
    );
    PlannedInterview interview = applicationService.createForTenant(
        principal.tenantId(),
        candidateId,
        jd,
        resume,
        llmProvider
    );
    auditService.record(
        principal,
        CREATE_TOOL,
        interview.history().session().id(),
        McpAuditOutcome.SUCCEEDED
    );
    return McpInterviewStatusResponse.from(interview);
  }

  @McpTool(
      name = STATUS_TOOL,
      description = "Get an adaptive interview status owned by the authenticated tenant"
  )
  public McpInterviewStatusResponse getStatus(
      McpSyncRequestContext context,
      @McpToolParam(description = "Interview session identifier") String sessionId
  ) {
    McpTenantPrincipal principal = requireScope(
        context,
        STATUS_TOOL,
        McpInterviewScope.INTERVIEW_READ
    );
    PlannedInterview interview = findInterview(principal, STATUS_TOOL, sessionId);
    auditService.record(
        principal,
        STATUS_TOOL,
        sessionId,
        McpAuditOutcome.SUCCEEDED
    );
    return McpInterviewStatusResponse.from(interview);
  }

  @McpTool(
      name = DIMENSIONS_TOOL,
      description = "List planned dimensions for an interview owned by the authenticated tenant"
  )
  public List<McpInterviewDimensionResponse> listDimensions(
      McpSyncRequestContext context,
      @McpToolParam(description = "Interview session identifier") String sessionId
  ) {
    McpTenantPrincipal principal = requireScope(
        context,
        DIMENSIONS_TOOL,
        McpInterviewScope.INTERVIEW_READ
    );
    PlannedInterview interview = findInterview(principal, DIMENSIONS_TOOL, sessionId);
    auditService.record(
        principal,
        DIMENSIONS_TOOL,
        sessionId,
        McpAuditOutcome.SUCCEEDED
    );
    return interview.plan().dimensions().stream()
        .map(McpInterviewDimensionResponse::from)
        .toList();
  }

  private PlannedInterview findInterview(
      McpTenantPrincipal principal,
      String toolName,
      String sessionId
  ) {
    try {
      return applicationService.getForTenant(principal.tenantId(), sessionId);
    } catch (BusinessException e) {
      if (!ErrorCode.INTERVIEW_SESSION_NOT_FOUND.getCode().equals(e.getCode())) {
        throw e;
      }
      auditService.record(principal, toolName, sessionId, McpAuditOutcome.NOT_FOUND);
      throw e;
    }
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

  private void validateCreateInput(
      String candidateId,
      String jd,
      String resume,
      String llmProvider
  ) {
    if (candidateId == null || candidateId.isBlank() || candidateId.length() > 64) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "候选人标识无效");
    }
    if (jd == null || jd.isBlank() || resume == null || resume.isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "JD 和简历不能为空");
    }
    if (llmProvider != null && llmProvider.length() > 64) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "LLM Provider 标识无效");
    }
  }
}
