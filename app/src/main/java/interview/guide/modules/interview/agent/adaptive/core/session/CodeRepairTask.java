package interview.guide.modules.interview.agent.adaptive.core.session;

import java.util.List;
import java.util.HashSet;

/** 原始代码任务的正式事实；reviewGuide 仅供服务端审阅。 */
public record CodeRepairTask(
    String initialCode,
    List<String> requirements,
    List<String> assumptions,
    ReviewGuide reviewGuide
) {

  public CodeRepairTask {
    requirements = immutable(requirements);
    assumptions = immutable(assumptions);
  }

  public enum QuestionType { TEXT, CODE_REPAIR }

  public record ReviewGuide(List<Check> checks) {
    public ReviewGuide {
      checks = immutable(checks);
    }
  }

  public record Check(String id, String defect, String trigger, String acceptance) {}

  public void validate() {
    requireText(initialCode, "initialCode");
    requireTexts(requirements, "requirements");
    requireTexts(assumptions, "assumptions");
    if (reviewGuide == null || reviewGuide.checks() == null || reviewGuide.checks().isEmpty()) {
      throw new IllegalArgumentException("reviewGuide.checks 不能为空");
    }
    var ids = new HashSet<String>();
    for (Check check : reviewGuide.checks()) {
      validateCheck(check);
      if (!ids.add(check.id())) throw new IllegalArgumentException("reviewGuide.checks ID 重复");
    }
  }

  private static void validateCheck(Check check) {
    if (check == null) throw new IllegalArgumentException("reviewGuide.checks 元素不能为空");
    requireText(check.id(), "check.id");
    requireText(check.defect(), "check.defect");
    requireText(check.trigger(), "check.trigger");
    requireText(check.acceptance(), "check.acceptance");
  }

  private static void requireTexts(List<String> values, String field) {
    if (values == null || values.isEmpty()) throw new IllegalArgumentException(field + " 不能为空");
    values.forEach(value -> requireText(value, field));
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
  }

  // 保留非法 null 元素，交由模型提案校验显式回流，而非在反序列化中丢弃。
  private static <T> List<T> immutable(List<T> values) {
    return values == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(values));
  }
}
