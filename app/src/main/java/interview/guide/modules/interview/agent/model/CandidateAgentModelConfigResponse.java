package interview.guide.modules.interview.agent.model;

public record CandidateAgentModelConfigResponse(
    boolean configured,
    String baseUrl,
    String maskedApiKey,
    String model,
    Double temperature
) {

  public static CandidateAgentModelConfigResponse unconfigured() {
    return new CandidateAgentModelConfigResponse(false, null, null, null, null);
  }
}
