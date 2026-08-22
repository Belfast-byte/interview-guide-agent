package interview.guide.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.auth.domain.UserRole;
import interview.guide.modules.auth.persistence.UserEntity;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

  private static final String SECRET =
      "dGVzdC1qd3Qtc2VjcmV0LW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXMtbG9uZw==";

  @Test
  @DisplayName("签发的 JWT 可还原用户主键与角色")
  void shouldRoundTripPrincipal() {
    JwtTokenService service = new JwtTokenService(
        new JwtProperties(SECRET, Duration.ofDays(7))
    );
    UserEntity user = new UserEntity(
        "candidate@example.com",
        "password-hash",
        UserRole.CANDIDATE
    );

    AuthenticatedUser principal = service.parse(service.issue(user));

    assertThat(principal.candidateId()).isEqualTo(user.id());
    assertThat(principal.role()).isEqualTo(UserRole.CANDIDATE);
    assertThat(service.expiresInSeconds()).isEqualTo(Duration.ofDays(7).toSeconds());
  }
}
