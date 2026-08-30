package interview.guide.modules.interview.agent.adaptive.planning;

import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.CandidateLevel;

/** 招聘阶段对应的默认深度。 */
public record InterviewLevelProfile(
    DepthLevel expectedDepth,
    DepthLevel depthCeiling
) {

  public static InterviewLevelProfile forLevel(CandidateLevel level) {
    return switch (level) {
      case INTERN -> new InterviewLevelProfile(DepthLevel.L1, DepthLevel.L2);
      case CAMPUS -> new InterviewLevelProfile(DepthLevel.L2, DepthLevel.L3);
      case EXPERIENCED -> new InterviewLevelProfile(DepthLevel.L3, DepthLevel.L4);
    };
  }

  CapabilityTarget.Depth depth() {
    return new CapabilityTarget.Depth(expectedDepth, depthCeiling);
  }
}
