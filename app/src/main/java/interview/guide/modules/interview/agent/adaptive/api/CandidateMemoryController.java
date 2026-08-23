package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.common.result.Result;
import interview.guide.common.security.AuthenticatedUser;
import interview.guide.modules.interview.agent.adaptive.application.CandidateMemoryQueryService;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 当前登录候选人的长期记忆查询入口。 */
@RestController
@RequestMapping("/api/adaptive-agent-interviews/me/memory")
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.interview.adaptive-agent",
    name = "enabled",
    havingValue = "true"
)
public class CandidateMemoryController {

  private final CandidateMemoryQueryService queryService;

  @GetMapping
  public Result<CandidateMemoryResponse> get(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam(defaultValue = "0")
      @Min(value = 0, message = "页码不能小于 0") int page
  ) {
    MemoryOwner owner = new MemoryOwner(null, principal.candidateId().toString());
    return Result.success(CandidateMemoryResponse.from(queryService.read(owner, page)));
  }
}
