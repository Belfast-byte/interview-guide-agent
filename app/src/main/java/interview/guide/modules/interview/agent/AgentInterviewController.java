package interview.guide.modules.interview.agent;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.result.Result;
import interview.guide.common.security.AuthenticatedUser;
import interview.guide.modules.interview.agent.model.AgentInterviewSessionResponse;
import interview.guide.modules.interview.agent.model.CreateAgentInterviewRequest;
import interview.guide.modules.interview.agent.model.SubmitAgentAnswerRequest;
import interview.guide.modules.interview.agent.runtime.InterviewAgentLoop;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 面试（agent-loop-v2）REST 控制器，提供创建会话、提交回答、查询会话的 HTTP 接口。
 */
@RestController
@RequestMapping("/api/interview/agent-loop/sessions")
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.interview.agent-loop",
    name = "enabled",
    havingValue = "true"
)
public class AgentInterviewController {

  private final InterviewAgentLoop interviewAgentLoop;

  @PostMapping
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 5)
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 5)
  public Result<AgentInterviewSessionResponse> createSession(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody CreateAgentInterviewRequest request
  ) {
    return Result.success(AgentInterviewSessionResponse.from(
        interviewAgentLoop.createSession(principal.candidateId(), request.jd(), request.resume())
    ));
  }

  @PostMapping("/{sessionId}/answers")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 10)
  public Result<AgentInterviewSessionResponse> submitAnswer(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String sessionId,
      @Valid @RequestBody SubmitAgentAnswerRequest request
  ) {
    return Result.success(AgentInterviewSessionResponse.from(
        interviewAgentLoop.submitAnswer(principal.candidateId(), sessionId, request.answer())
    ));
  }

  @GetMapping("/{sessionId}")
  public Result<AgentInterviewSessionResponse> getSession(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String sessionId
  ) {
    return Result.success(AgentInterviewSessionResponse.from(
        interviewAgentLoop.getSession(principal.candidateId(), sessionId)
    ));
  }
}
