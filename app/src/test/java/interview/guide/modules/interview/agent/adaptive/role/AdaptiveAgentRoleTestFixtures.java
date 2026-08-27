package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerContext;
import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerWorkView;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.ProjectInterviewContext;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActModelContext;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolObservation;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

final class AdaptiveAgentRoleTestFixtures {

  private static final String SESSION_ID = "session-1";
  private static final String PROVIDER_ID = "provider-1";
  private static final int MAX_TURNS = 6;

  private AdaptiveAgentRoleTestFixtures() {}

  static ChatResponse response(String content) {
    return response(new AssistantMessage(content));
  }

  static ChatResponse response(AssistantMessage message) {
    return new ChatResponse(List.of(new Generation(message)));
  }

  static ReActModelContext context(CandidateAnswer answer) {
    return context(answer, List.of());
  }

  static ReActModelContext context(
      CandidateAnswer answer,
      List<ToolObservation> observations
  ) {
    return new ReActModelContext(
        request(defaultInterviewerContext(answer)),
        observations
    );
  }

  static ToolObservation acceptedObservation() {
    return new ToolObservation(
        "load_skill",
        Map.of("skillId", "java"),
        true,
        "skill:java",
        "Java interviewer persona"
    );
  }

  static ReActModelContext contextAtTurn(int currentTurn) {
    return new ReActModelContext(
        request(new InterviewerContext(
            "JD",
            "Resume",
            currentTurn,
            MAX_TURNS,
            0,
            "专业基础",
            "缓存与并发",
            List.of("question_bank_search"),
            null,
            List.of(),
            null,
            plannedMemory(currentTurn + 1),
            null,
            null,
            null
        )),
        List.of()
    );
  }

  static ReActModelContext contextWithProject() {
    ProjectInterviewContext project = new ProjectInterviewContext(
        "digest-1",
        List.of(),
        List.of(new ProjectInterviewContext.ProjectScenario(
            "scenario-1",
            "缓存失效并发场景",
            "订单缓存存在版本号失效逻辑",
            "order/OrderCache.java:42",
            "EXPLAIN",
            "解释并发边界",
            null
        ))
    );
    return new ReActModelContext(request(projectInterviewerContext(project)), List.of());
  }

  private static ReActRequest request(InterviewerContext context) {
    return new ReActRequest(SESSION_ID, AgentRole.INTERVIEWER, PROVIDER_ID, context);
  }

  private static InterviewerContext defaultInterviewerContext(CandidateAnswer answer) {
    return new InterviewerContext(
        "JD",
        "Resume",
        answer == null ? 0 : 1,
        MAX_TURNS,
        0,
        "专业基础",
        "缓存与并发",
        List.of("question_bank_search"),
        null,
        List.of(),
        answer,
        plannedMemory(answer == null ? 1 : 2),
        null,
        null,
        null
    );
  }

  private static InterviewerContext projectInterviewerContext(
      ProjectInterviewContext project
  ) {
    return new InterviewerContext(
        "JD",
        "Resume",
        1,
        MAX_TURNS,
        0,
        "项目经验",
        "架构取舍",
        List.of(),
        null,
        List.of(),
        null,
        plannedMemory(2),
        null,
        null,
        project
    );
  }

  private static InterviewerWorkView plannedMemory(int turnIndex) {
    return new InterviewerWorkView(
        "target-0",
        new TopicKey("java-backend", "CACHE"),
        DepthLevel.L2,
        DepthLevel.L3,
        DepthLevel.L0,
        "缓存与并发",
        null,
        MAX_TURNS - turnIndex + 1,
        2,
        1
    );
  }
}
