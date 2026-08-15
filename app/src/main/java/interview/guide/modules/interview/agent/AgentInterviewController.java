package interview.guide.modules.interview.agent;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.result.Result;
import interview.guide.modules.interview.agent.model.AgentInterviewSessionResponse;
import interview.guide.modules.interview.agent.model.CreateAgentInterviewRequest;
import interview.guide.modules.interview.agent.model.SubmitAgentAnswerRequest;
import interview.guide.modules.interview.agent.runtime.InterviewAgentLoop;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
      @Valid @RequestBody CreateAgentInterviewRequest request
  ) {
    return Result.success(AgentInterviewSessionResponse.from(
        interviewAgentLoop.createSession(request.jd(), request.resume())
    ));
  }

  @PostMapping("/{sessionId}/answers")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 10)
  public Result<AgentInterviewSessionResponse> submitAnswer(
      @PathVariable String sessionId,
      @Valid @RequestBody SubmitAgentAnswerRequest request
  ) {
    return Result.success(AgentInterviewSessionResponse.from(
        interviewAgentLoop.submitAnswer(sessionId, request.answer())
    ));
  }

  @GetMapping("/{sessionId}")
  public Result<AgentInterviewSessionResponse> getSession(@PathVariable String sessionId) {
    return Result.success(AgentInterviewSessionResponse.from(
        interviewAgentLoop.getSession(sessionId)
    ));
  }
}
