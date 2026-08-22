package interview.guide.modules.voiceinterview.config;

import interview.guide.common.security.AuthenticatedUser;
import interview.guide.common.security.JwtTokenService;
import interview.guide.modules.auth.domain.UserRole;
import interview.guide.modules.voiceinterview.service.VoiceInterviewService;
import java.net.URI;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtWebSocketHandshakeInterceptorTest {

  @Mock
  private JwtTokenService tokenService;
  @Mock
  private ServerHttpRequest request;
  @Mock
  private ServerHttpResponse response;
  @Mock
  private WebSocketHandler handler;
  @Mock
  private VoiceInterviewService interviewService;

  @Test
  @DisplayName("候选人 JWT 通过 WebSocket 子协议注入握手身份")
  void shouldAuthenticateCandidateProtocol() {
    UUID candidateId = UUID.randomUUID();
    HttpHeaders headers = new HttpHeaders();
    headers.add("Sec-WebSocket-Protocol", "bearer, jwt-token");
    when(request.getHeaders()).thenReturn(headers);
    when(request.getURI()).thenReturn(URI.create("http://localhost/ws/voice-interview/12"));
    when(tokenService.parse("jwt-token"))
        .thenReturn(new AuthenticatedUser(candidateId, UserRole.CANDIDATE));
    var attributes = new HashMap<String, Object>();

    boolean accepted = interceptor().beforeHandshake(request, response, handler, attributes);

    assertThat(accepted).isTrue();
    assertThat(attributes.get(JwtWebSocketHandshakeInterceptor.PRINCIPAL_ATTRIBUTE))
        .isEqualTo(new AuthenticatedUser(candidateId, UserRole.CANDIDATE));
    verify(interviewService).getSession(candidateId, 12L);
  }

  @Test
  @DisplayName("缺少 JWT 子协议时拒绝 WebSocket 握手")
  void shouldRejectMissingProtocol() {
    when(request.getHeaders()).thenReturn(new HttpHeaders());

    boolean accepted = interceptor().beforeHandshake(
        request, response, handler, new HashMap<>());

    assertThat(accepted).isFalse();
    verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
  }

  private JwtWebSocketHandshakeInterceptor interceptor() {
    return new JwtWebSocketHandshakeInterceptor(tokenService, interviewService);
  }
}
