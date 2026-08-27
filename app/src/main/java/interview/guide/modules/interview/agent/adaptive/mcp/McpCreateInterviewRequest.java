package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.CandidateLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.InterviewSessionSettings;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import java.util.List;

/** MCP 创建面试参数。 */
public record McpCreateInterviewRequest(
    String candidateId,
    String jd,
    String resume,
    String llmProvider,
    SessionMode mode,
    CandidateLevel candidateLevel,
    List<TopicKey> practiceScope
) {

  InterviewSessionSettings settings() {
    return new InterviewSessionSettings(
        mode,
        candidateLevel,
        new PracticeScope(practiceScope)
    );
  }
}
