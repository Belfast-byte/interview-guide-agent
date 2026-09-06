package interview.guide.modules.interview.agent.adaptive.core.session;

/** 回答事实与处理进度分开；失败或租约过期后仅允许处理同一份答案。 */
public enum AnswerProcessingStatus { WAITING, PROCESSING, RETRYABLE, COMPLETED }
