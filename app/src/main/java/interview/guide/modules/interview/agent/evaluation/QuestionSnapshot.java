package interview.guide.modules.interview.agent.evaluation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;

/** 离线派生材料，不携带账号、会话凭据、私有答案或当前回答。 */
public record QuestionSnapshot(
    String sampleId, Source source, int turnIndex, String questionType,
    PublicQuestion publicQuestion, String target, String gap,
    List<History> history, boolean historyComplete, String generatorModel, String generatorPromptVersion
) {
  public enum Source { FIXTURE, PUBLISHED }

  public record PublicQuestion(String content, String reason, List<String> context) {
    public PublicQuestion {
      requireText(content);
      context = List.copyOf(context);
      context.forEach(QuestionSnapshot::requireText);
    }
  }

  /** verification 仅接受生成当时的正式来源；不能恢复时两字段都为 null。 */
  public record History(int turnIndex, String question, String verification, String verificationSource) {
    public History {
      requireText(question);
      if (turnIndex < 1 || (verification == null) != (verificationSource == null)) {
        throw new IllegalArgumentException("Invalid history provenance");
      }
      if (verification != null) { requireText(verification); requireText(verificationSource); }
    }
  }

  public QuestionSnapshot {
    requireText(sampleId);
    requireText(questionType);
    Objects.requireNonNull(source);
    Objects.requireNonNull(publicQuestion);
    if (turnIndex < 1) throw new IllegalArgumentException("Invalid turn");
    history = List.copyOf(history);
    var indices = new HashSet<Integer>();
    for (var item : history) {
      if (item.turnIndex() >= turnIndex || !indices.add(item.turnIndex())) {
        throw new IllegalArgumentException("Future or duplicate history");
      }
    }
    if (historyComplete && history.size() != turnIndex - 1) {
      throw new IllegalArgumentException("Incomplete history cannot be marked complete");
    }
  }

  public String hash(ObjectMapper json) { return digest(json.writeValueAsString(this)); }
  public String publicHash(ObjectMapper json) { return digest(json.writeValueAsString(publicQuestion)); }

  /** 只允许从实际送入 Judge 的字段引用，不允许任意 JSON 路径访问。 */
  public Map<String, String> sources() {
    var result = new LinkedHashMap<String, String>();
    result.put("publicQuestion.content", publicQuestion.content());
    if (publicQuestion.reason() != null) result.put("publicQuestion.reason", publicQuestion.reason());
    for (int i = 0; i < publicQuestion.context().size(); i++) {
      result.put("publicQuestion.context[" + i + "]", publicQuestion.context().get(i));
    }
    if (target != null) result.put("target", target);
    if (gap != null) result.put("gap", gap);
    for (int i = 0; i < history.size(); i++) {
      result.put("history[" + i + "].question", history.get(i).question());
      if (history.get(i).verification() != null) {
        result.put("history[" + i + "].verification", history.get(i).verification());
      }
    }
    return Map.copyOf(result);
  }

  static void requireText(String text) {
    if (text == null || text.isBlank()) throw new IllegalArgumentException("Required text missing");
  }

  static String digest(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
}
