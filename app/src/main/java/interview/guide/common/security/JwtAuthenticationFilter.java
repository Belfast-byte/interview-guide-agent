package interview.guide.common.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenService tokenService;
  private final AuthenticationEntryPoint authenticationEntryPoint;

  public JwtAuthenticationFilter(
      JwtTokenService tokenService,
      AuthenticationEntryPoint authenticationEntryPoint
  ) {
    this.tokenService = tokenService;
    this.authenticationEntryPoint = authenticationEntryPoint;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String authorization = request.getHeader("Authorization");
    if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }
    try {
      AuthenticatedUser principal = tokenService.parse(
          authorization.substring(BEARER_PREFIX.length())
      );
      var authority = new SimpleGrantedAuthority("ROLE_" + principal.role().name());
      var authentication = new UsernamePasswordAuthenticationToken(
          principal,
          null,
          List.of(authority)
      );
      SecurityContextHolder.getContext().setAuthentication(authentication);
      filterChain.doFilter(request, response);
    } catch (JwtException | IllegalArgumentException exception) {
      SecurityContextHolder.clearContext();
      authenticationEntryPoint.commence(
          request,
          response,
          new org.springframework.security.authentication.BadCredentialsException(
              "JWT 无效",
              exception
          )
      );
    }
  }
}
