package interview.guide.modules.interview.agent.adaptive.core.memory;

/** 确定性策略可选择的下一动作。 */
public enum NextActionType {
  WAIT,
  RESUME_INTENT,
  ASK,
  CALL_TOOL,
  SWITCH_TARGET,
  FINISH
}
