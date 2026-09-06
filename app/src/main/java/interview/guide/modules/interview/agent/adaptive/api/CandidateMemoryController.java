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
  private final interview.guide.modules.interview.agent.adaptive.persistence.memory.JpaMemoryEvidenceService memoryEvidence;

  @GetMapping("/observations/{revisionId}")
  public Result<java.util.List<interview.guide.modules.interview.agent.adaptive.persistence.memory.MemoryObservationRevision>> audit(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @org.springframework.web.bind.annotation.PathVariable long revisionId) {
    return Result.success(memoryEvidence.audit(new MemoryOwner(null,principal.candidateId().toString()),revisionId));
  }

  @org.springframework.web.bind.annotation.PostMapping("/observations/{revisionId}/retract")
  public Result<Void> retract(@AuthenticationPrincipal AuthenticatedUser principal,
      @org.springframework.web.bind.annotation.PathVariable long revisionId,
      @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody Retraction input) {
    memoryEvidence.retract(new MemoryOwner(null,principal.candidateId().toString()),revisionId,input.reason());
    return Result.success();
  }

  @org.springframework.web.bind.annotation.PostMapping("/observations/{revisionId}/rebuild")
  public Result<Void> rebuild(@AuthenticationPrincipal AuthenticatedUser principal,
      @org.springframework.web.bind.annotation.PathVariable long revisionId) {
    memoryEvidence.rebuild(new MemoryOwner(null,principal.candidateId().toString()),revisionId);
    return Result.success();
  }

  public record Retraction(@jakarta.validation.constraints.NotBlank
      @jakarta.validation.constraints.Size(max=500) String reason) {}


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
