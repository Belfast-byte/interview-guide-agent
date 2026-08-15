package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.util.Set;

public interface CodeAnchorCatalog {

  Set<CodeAnchor> findMissing(String repositoryRef, Set<CodeAnchor> anchors);
}
