package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.util.List;

public record CodeTraceResult(String query, List<CodeTraceMatch> matches) {

  public CodeTraceResult {
    matches = List.copyOf(matches);
  }
}
