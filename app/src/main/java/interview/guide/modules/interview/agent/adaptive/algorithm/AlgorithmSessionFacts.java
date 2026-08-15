package interview.guide.modules.interview.agent.adaptive.algorithm;

public interface AlgorithmSessionFacts {

  long lockCurrentTurn(String sessionId, int turnIndex);
}
