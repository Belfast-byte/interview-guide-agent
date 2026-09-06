package interview.guide.modules.interview.agent.adaptive.rubric;

import interview.guide.common.result.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/knowledgebase/rubric-generation")
public class RubricReviewController {
  private final RubricGenerationStore store;
  public RubricReviewController(RubricGenerationStore store) { this.store=store; }
  @GetMapping
  public Result<java.util.List<RubricGenerationStore.Audit>> list(
      @RequestParam(defaultValue="0") int page) {
    if(page<0) throw new IllegalArgumentException("page must be non-negative");
    return Result.success(store.list(page));
  }
  @PostMapping("/{id}/review")
  public Result<RubricGenerationStore.Audit> review(@PathVariable String id,
      @jakarta.validation.Valid @RequestBody ReviewRequest request, java.security.Principal principal) {
    return Result.success(store.manualReview(id,request.approved(),request.reason(),
        request.rubricVersion(),principal.getName()));
  }
  public record ReviewRequest(boolean approved,
      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=2000) String reason,
      @jakarta.validation.constraints.Pattern(regexp="[0-9a-f]{64}")
      @jakarta.validation.constraints.NotNull String rubricVersion) {}
  @GetMapping("/{id}")
  public Result<RubricGenerationStore.Audit> audit(@PathVariable String id) {
    return Result.success(store.audit(id));
  }
}
