package interview.guide.modules.interview.agent;

import interview.guide.common.result.Result;
import interview.guide.common.security.AuthenticatedUser;
import interview.guide.modules.interview.agent.model.CandidateAgentModelConfigRequest;
import interview.guide.modules.interview.agent.model.CandidateAgentModelConfigResponse;
import interview.guide.modules.interview.agent.runtime.CandidateAgentModelConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview/agent-loop/model-config")
@RequiredArgsConstructor
public class CandidateAgentModelConfigController {

  private final CandidateAgentModelConfigService configService;

  @GetMapping
  public Result<CandidateAgentModelConfigResponse> get(
      @AuthenticationPrincipal AuthenticatedUser principal
  ) {
    return Result.success(configService.get(principal.candidateId()));
  }

  @PutMapping
  public Result<CandidateAgentModelConfigResponse> save(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody CandidateAgentModelConfigRequest request
  ) {
    return Result.success(configService.save(principal.candidateId(), request));
  }
}
