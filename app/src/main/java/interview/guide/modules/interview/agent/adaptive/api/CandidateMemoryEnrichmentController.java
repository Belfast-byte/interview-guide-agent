package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.common.result.Result;
import interview.guide.common.security.AuthenticatedUser;
import interview.guide.modules.interview.agent.adaptive.application.EpisodeEnrichmentRecoveryService;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前候选人显式重试 FAILED Episode enrichment 的入口。
 */
@RestController
@RequestMapping("/api/adaptive-agent-interviews/me/memory/episodes")
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.interview.adaptive-agent",
    name = "enabled",
    havingValue = "true"
)
public class CandidateMemoryEnrichmentController {

  private final EpisodeEnrichmentRecoveryService recoveryService;

  @PostMapping("/{episodeId}/enrichment/retry")
  public Result<Void> retry(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable long episodeId
  ) {
    MemoryOwner owner = new MemoryOwner(null, principal.candidateId().toString());
    recoveryService.retry(owner, episodeId);
    return Result.success();
  }
}
