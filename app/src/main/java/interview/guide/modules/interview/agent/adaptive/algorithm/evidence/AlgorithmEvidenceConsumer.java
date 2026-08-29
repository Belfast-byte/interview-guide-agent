package interview.guide.modules.interview.agent.adaptive.algorithm.evidence;

/** Consumes a terminal sandbox execution into the assessment evidence chain. */
public interface AlgorithmEvidenceConsumer {

  boolean consume(String executionId);
}
