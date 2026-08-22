package interview.guide.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import interview.guide.modules.auth.domain.UserRole;
import interview.guide.modules.auth.persistence.UserEntity;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

  private static final String SECRET =
      "dGVzdC1qd3Qtc2VjcmV0LW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXMtbG9uZw==";

  private final JwtTokenService tokenService = new JwtTokenService(
      new JwtProperties(SECRET, Duration.ofDays(7))
  );
  private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
      tokenService,
      new RestAuthenticationEntryPoint()
  );

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("有效 Bearer Token 写入当前用户和角色")
  void shouldAuthenticateValidBearerToken() throws Exception {
    UserEntity user = new UserEntity("candidate@example.com", "hash", UserRole.CANDIDATE);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + tokenService.issue(user));
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication.getPrincipal()).isEqualTo(new AuthenticatedUser(
        user.id(),
        UserRole.CANDIDATE
    ));
    assertThat(authentication.getAuthorities())
        .extracting(Object::toString)
        .containsExactly("ROLE_CANDIDATE");
    verify(chain).doFilter(request, response);
  }

  @Test
  @DisplayName("无效 Bearer Token 立即返回 401 且不进入业务链")
  void shouldRejectInvalidBearerToken() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer invalid-token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("\"code\":401");
    verify(chain, never()).doFilter(request, response);
  }
}
