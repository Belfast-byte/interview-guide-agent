package interview.guide.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 哈希工具。
 */
public final class Sha256 {

  private Sha256() {}

  /**
   * 计算字符串的 SHA-256 十六进制摘要。
   *
   * @param value 原始字符串
   * @return 小写十六进制摘要
   */
  public static String hex(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
      );
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
