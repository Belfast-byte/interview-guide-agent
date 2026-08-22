package interview.guide.modules.voiceinterview.config;

import interview.guide.common.security.AuthenticatedUser;
import interview.guide.common.security.JwtTokenService;
import interview.guide.modules.auth.domain.UserRole;
import interview.guide.modules.voiceinterview.service.VoiceInterviewService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class JwtWebSocketHandshakeInterceptor implements HandshakeInterceptor {

  public static final String PRINCIPAL_ATTRIBUTE = "authenticatedUser";
  private static final String PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
  private static final String BEARER_PROTOCOL = "bearer";
  private static final int PROTOCOL_PARTS = 2;

  private final JwtTokenService tokenService;
  private final VoiceInterviewService interviewService;

  public JwtWebSocketHandshakeInterceptor(
      JwtTokenService tokenService,
      VoiceInterviewService interviewService
  ) {
    this.tokenService = tokenService;
    this.interviewService = interviewService;
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Map<String, Object> attributes
  ) {
    String header = request.getHeaders().getFirst(PROTOCOL_HEADER);
    String[] protocols = header == null ? new String[0] : header.split(",");
    if (protocols.length != PROTOCOL_PARTS
        || !BEARER_PROTOCOL.equals(protocols[0].trim())) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }

    try {
      AuthenticatedUser principal = tokenService.parse(
          protocols[1].trim());
      if (principal.role() != UserRole.CANDIDATE) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
      }
      requireOwnership(request, principal);
      attributes.put(PRINCIPAL_ATTRIBUTE, principal);
      return true;
    } catch (RuntimeException exception) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }
  }

  private void requireOwnership(ServerHttpRequest request, AuthenticatedUser principal) {
    String path = request.getURI().getPath();
    String sessionId = path.substring(path.lastIndexOf('/') + 1);
    interviewService.getSession(principal.candidateId(), Long.parseLong(sessionId));
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Exception exception
  ) {
  }
}
