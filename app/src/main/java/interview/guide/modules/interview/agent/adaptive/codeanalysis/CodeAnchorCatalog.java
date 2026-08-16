package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.util.Set;

/**
 * 代码锚点目录接口。
 */
public interface CodeAnchorCatalog {

  Set<CodeAnchor> findMissing(String repositoryRef, Set<CodeAnchor> anchors);
}
