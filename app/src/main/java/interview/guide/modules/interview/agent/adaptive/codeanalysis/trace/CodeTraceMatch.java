package interview.guide.modules.interview.agent.adaptive.codeanalysis.trace;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnchor;

/**
 * 代码轨迹匹配结果。
 */
public record CodeTraceMatch(CodeAnchor anchor, String sourceLine) {}
