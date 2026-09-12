package interview.guide.modules.interview.agent.adaptive.core.context;

import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** 对题目各项验收语义的模型静态审阅，不表示代码执行结果。 */
public record CodeRepairReview(List<CheckReview> checks) {
  public enum Result { SATISFIED, NOT_SATISFIED, UNDETERMINED }
  public record CheckReview(String checkId, Result result, String reason) {}

  public CodeRepairReview {
    checks = checks == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(checks));
  }

  public void validate(CodeRepairTask task) {
    if (checks == null || checks.isEmpty()) throw new IllegalArgumentException("codeReview.checks 不能为空");
    Set<String> expected = task.reviewGuide().checks().stream().map(CodeRepairTask.Check::id)
        .collect(Collectors.toSet());
    var seen = new HashSet<String>();
    for (CheckReview check : checks) {
      if (check == null || check.checkId() == null || !expected.contains(check.checkId())
          || !seen.add(check.checkId()) || check.result() == null
          || check.reason() == null || check.reason().isBlank()) {
        throw new IllegalArgumentException("代码审阅必须包含已知且不重复的 checkId、结论和理由");
      }
    }
    if (!seen.equals(expected)) throw new IllegalArgumentException("代码审阅遗漏题目验收项");
  }
}
