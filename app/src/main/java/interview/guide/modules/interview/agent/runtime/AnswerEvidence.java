package interview.guide.modules.interview.agent.runtime;

/**
 * 回答证据值对象，保存评估时从回答中抽取的原文引用与结论。
 */
public record AnswerEvidence(String finding, String quote) {}
