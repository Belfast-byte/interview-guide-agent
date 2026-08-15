package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.util.List;

public interface CodeTraceSource {

  List<CodeTraceMatch> trace(String repositoryRef, String query, int limit);
}
