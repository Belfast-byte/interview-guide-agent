package interview.guide.modules.interview.agent.runtime;

/**
 * 面试记录中的一轮数据，保存问题、回答与评估结论。
 */
public record InterviewTranscriptTurn(int turnNumber, String question, String answer) {}
