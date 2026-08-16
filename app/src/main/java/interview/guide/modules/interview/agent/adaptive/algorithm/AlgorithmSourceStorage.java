package interview.guide.modules.interview.agent.adaptive.algorithm;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.file.FileStorageService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 算法源码存储。
 */
@Component
@RequiredArgsConstructor
public class AlgorithmSourceStorage {

  private final FileStorageService fileStorageService;
  private final AlgorithmInterviewProperties properties;

  public StoredAlgorithmSource store(
      String sessionId,
      SandboxLanguage language,
      String source
  ) {
    byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > properties.getMaxSourceBytes()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "源码不能超过 64KB");
    }
    String codeHash = sha256(bytes);
    String codeRef = "sandbox/sources/%s/%s.%s".formatted(
        sessionId,
        UUID.randomUUID(),
        extension(language)
    );
    fileStorageService.uploadFile(codeRef, bytes, "text/plain; charset=UTF-8");
    return new StoredAlgorithmSource(codeRef, codeHash);
  }

  private String extension(SandboxLanguage language) {
    return switch (language) {
      case JAVA -> "java";
      case PYTHON -> "py";
      case CPP -> "cpp";
    };
  }

  private String sha256(byte[] source) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
