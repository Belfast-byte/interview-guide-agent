package interview.guide.modules.interview.agent.adaptive.algorithm;

/**
 * 算法会话事实接口。
 */
public interface AlgorithmSessionFacts {

  long lockCurrentTurn(String sessionId, int turnIndex);

  int turnIndex(long turnId);

  long turnId(String sessionId, int turnIndex);
}
