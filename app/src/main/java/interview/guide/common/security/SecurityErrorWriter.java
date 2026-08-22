package interview.guide.common.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

final class SecurityErrorWriter {

  private SecurityErrorWriter() {}

  static void write(HttpServletResponse response, int status, String message)
      throws IOException {
    response.setStatus(status);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(
        "{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}"
    );
  }
}
