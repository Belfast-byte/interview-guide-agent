package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.util.List;

/**
 * 代码轨迹来源接口。
 */
public interface CodeTraceSource {

  List<CodeTraceMatch> trace(String repositoryRef, String query, int limit);
}
