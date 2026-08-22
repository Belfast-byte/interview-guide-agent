package interview.guide.modules.voiceinterview.config;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Component
public class JwtWebSocketHandshakeHandler extends DefaultHandshakeHandler {

  public JwtWebSocketHandshakeHandler() {
    setSupportedProtocols("bearer");
  }
}
