package interview.guide.modules.auth.api;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.result.Result;
import interview.guide.common.security.AuthenticatedUser;
import interview.guide.modules.auth.application.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/register")
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 5)
  public Result<CurrentUserResponse> register(
      @Valid @RequestBody RegisterRequest request
  ) {
    return Result.success(CurrentUserResponse.from(
        authService.register(request.email(), request.password())
    ));
  }

  @PostMapping("/login")
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 10)
  public Result<AccessTokenResponse> login(
      @Valid @RequestBody LoginRequest request
  ) {
    return Result.success(authService.login(request.email(), request.password()));
  }

  @GetMapping("/me")
  public Result<CurrentUserResponse> me(
      @AuthenticationPrincipal AuthenticatedUser principal
  ) {
    return Result.success(CurrentUserResponse.from(
        authService.currentUser(principal.candidateId())
    ));
  }
}
