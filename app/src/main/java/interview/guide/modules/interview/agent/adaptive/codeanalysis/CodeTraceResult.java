package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.util.List;

/**
 * 代码轨迹分析结果。
 */
public record CodeTraceResult(String query, List<CodeTraceMatch> matches) {

  public CodeTraceResult {
    matches = List.copyOf(matches);
  }
}
