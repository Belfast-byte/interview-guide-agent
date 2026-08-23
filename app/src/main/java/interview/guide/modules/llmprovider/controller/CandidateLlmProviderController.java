package interview.guide.modules.llmprovider.controller;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.result.Result;
import interview.guide.common.security.AuthenticatedUser;
import interview.guide.modules.llmprovider.dto.CandidateProviderResponse;
import interview.guide.modules.llmprovider.dto.CreateCandidateProviderRequest;
import interview.guide.modules.llmprovider.dto.ProviderTestResult;
import interview.guide.modules.llmprovider.dto.UpdateCandidateProviderRequest;
import interview.guide.modules.llmprovider.service.CandidateLlmProviderService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/llm-providers")
@RequiredArgsConstructor
public class CandidateLlmProviderController {

  private final CandidateLlmProviderService providerService;

  @GetMapping
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 30)
  public Result<List<CandidateProviderResponse>> list(
      @AuthenticationPrincipal AuthenticatedUser principal
  ) {
    return Result.success(providerService.list(principal.candidateId()));
  }

  @PostMapping
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 5)
  public Result<Void> create(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody CreateCandidateProviderRequest request
  ) {
    providerService.create(principal.candidateId(), request);
    return Result.success();
  }

  @PutMapping("/{providerId}")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 5)
  public Result<Void> update(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String providerId,
      @Valid @RequestBody UpdateCandidateProviderRequest request
  ) {
    providerService.update(principal.candidateId(), providerId, request);
    return Result.success();
  }

  @PostMapping("/{providerId}/test")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 10)
  public Result<ProviderTestResult> test(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String providerId
  ) {
    return Result.success(providerService.test(principal.candidateId(), providerId));
  }

  @DeleteMapping("/{providerId}")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 5)
  public Result<Void> delete(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String providerId
  ) {
    providerService.delete(principal.candidateId(), providerId);
    return Result.success();
  }

  @PutMapping("/{providerId}/default-chat")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 5)
  public Result<Void> setDefaultChat(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String providerId
  ) {
    providerService.setDefaultChat(principal.candidateId(), providerId);
    return Result.success();
  }

  @PutMapping("/{providerId}/default-embedding")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 5)
  public Result<Void> setDefaultEmbedding(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String providerId
  ) {
    providerService.setDefaultEmbedding(principal.candidateId(), providerId);
    return Result.success();
  }
}
