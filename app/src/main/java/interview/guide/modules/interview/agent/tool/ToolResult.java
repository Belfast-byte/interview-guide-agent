package interview.guide.modules.interview.agent.tool;

import interview.guide.modules.interview.agent.runtime.LoadedSkill;

/**
 * 旧版工具调用结果，目前承载面试技能加载结果。
 */
public record ToolResult(String toolName, LoadedSkill loadedSkill) {}
