package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.session.AdoptedRubricSource;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseQuestionRepository;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

/** 发布题目时固定评分正文；索引版本漂移时拒绝发布，避免静默换用量规。 */
@Component
public class RubricSnapshotResolver {
  private final KnowledgeBaseQuestionRepository questions;
  public RubricSnapshotResolver(KnowledgeBaseQuestionRepository questions) {
    this.questions = questions;
  }
  public List<AdoptedRubricSource> resolve(List<String> references) {
    return references.stream().map(this::resolve).toList();
  }
  private AdoptedRubricSource resolve(String reference) {
    var source = AdoptedRubricSource.fromReference(reference);
    if (!source.entryId().matches("question:[0-9]+:rubric")) {
      throw new IllegalArgumentException("未知 rubric 来源");
    }
    long id = Long.parseLong(source.entryId().split(":")[1]);
    var question = questions.findById(id).orElseThrow();
    String body = question.getScoringRubric();
    if (question.getStatus() != KnowledgeBaseQuestionStatus.ACTIVE || body == null) {
      throw new IllegalStateException("采用的 rubric 已不可用");
    }
    body = body.replace("\r\n", "\n").replace('\r', '\n').strip();
    if (body.isBlank() || !version(body).equals(source.version())) {
      throw new IllegalStateException("采用的 rubric 版本已变化，请重新生成题目");
    }
    return new AdoptedRubricSource(reference, source.entryId(), source.version(), body);
  }
  public static String version(String body) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(body.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
