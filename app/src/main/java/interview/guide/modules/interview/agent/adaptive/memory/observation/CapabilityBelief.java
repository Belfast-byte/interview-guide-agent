package interview.guide.modules.interview.agent.adaptive.memory.observation;

import java.util.List;

public record CapabilityBelief(String capabilityKey,String objective,String state,boolean needsVerification,
      int independentOpportunities,String revision,List<Long> evidenceRevisionIds,String latestObservation,String updatedAt) {}
