package interview.guide.modules.interview.agent.adaptive.memory.episode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** 生成题目原文指纹与忽略常见问句框架后的场景指纹。 */
public final class QuestionFingerprint {

  private static final Pattern NON_CONTENT = Pattern.compile("[^\\p{L}\\p{N}]+");
  private static final Pattern QUESTION_FRAME = Pattern.compile(
      "请|你会|如何|怎么|为什么|什么是|说明|解释|谈谈|一下|如果|假设|中|的"
  );

  private QuestionFingerprint() {}

  public static String wording(String question) {
    return digest(normalize(question));
  }

  public static String scenario(String question) {
    return digest(QUESTION_FRAME.matcher(normalize(question)).replaceAll(""));
  }

  private static String normalize(String question) {
    if (question == null || question.isBlank()) {
      throw new IllegalArgumentException("question 不能为空");
    }
    return NON_CONTENT.matcher(question.toLowerCase(Locale.ROOT)).replaceAll("");
  }

  private static String digest(String content) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256")
          .digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JDK 缺少 SHA-256", e);
    }
  }
}
