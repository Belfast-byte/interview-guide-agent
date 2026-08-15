package interview.guide.modules.interview.agent.runtime;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.tool.InterviewToolGateway;
import interview.guide.modules.interview.agent.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewAgentLoopTest {

  private static final LoadedSkill JAVA_SKILL = new LoadedSkill(
      "java-backend",
      "Java 后端",
      "Java 后端岗位面试",
      "完整 SKILL.md body",
      "abc123"
  );
  private static final AssessmentResult ASSESSMENT = new AssessmentResult(
      AnswerDepthLevel.L3,
      new AnswerEvidence("说明了可重试的最终一致性方案", "让删除操作可重试"),
      AssessmentAction.FOLLOW_UP
  );

  @Mock
  private AgentModelGateway modelGateway;

  @Mock
  private AgentContextBuilder contextBuilder;

  @Mock
  private InterviewToolGateway toolGateway;

  @Mock
  private AgentInterviewPersistenceService persistenceService;

  private AgentInterviewRuntimeProperties runtimeProperties;

  private InterviewAgentLoop loop;

  @BeforeEach
  void setUp() {
    runtimeProperties = new AgentInterviewRuntimeProperties();
    loop = new InterviewAgentLoop(
        modelGateway,
        contextBuilder,
        toolGateway,
        persistenceService,
        runtimeProperties
    );
  }

  @Nested
  @DisplayName("创建会话")
  class CreateSession {

    @Test
    @DisplayName("模型先自主加载 Skill，再基于工具结果生成第一题")
    void shouldLoadSkillBeforeAskingFirstQuestion() {
      AgentLoopState created = state(0, null, List.of(), AgentLoopStatus.CREATED);
      InterviewAgentContext descriptorsOnly = context(0, null, List.of(), null);
      InterviewAgentContext withLoadedSkill = context(0, JAVA_SKILL, List.of(), null);
      AgentLoopState saved = state(
          1,
          JAVA_SKILL,
          List.of(new Turn(1, "请介绍一次你解决并发问题的经历？", null)),
          AgentLoopStatus.IN_PROGRESS
      );
      AgentStep.CallTool call = new AgentStep.CallTool(
          "load_skill",
          Map.of("skillId", "java-backend")
      );

      when(persistenceService.create("JD", "Resume", 6)).thenReturn(created);
      when(contextBuilder.build("sid", null, null))
          .thenReturn(descriptorsOnly, withLoadedSkill);
      when(modelGateway.nextStep(descriptorsOnly)).thenReturn(call);
      when(toolGateway.execute(call)).thenReturn(new ToolResult("load_skill", JAVA_SKILL));
      when(modelGateway.nextStep(withLoadedSkill))
          .thenReturn(new AgentStep.Ask("请介绍一次你解决并发问题的经历？"));
      when(persistenceService.get("sid")).thenReturn(saved);

      AgentLoopState result = loop.createSession("JD", "Resume");

      assertThat(result.currentQuestion()).isEqualTo("请介绍一次你解决并发问题的经历？");
      InOrder order = inOrder(
          modelGateway,
          toolGateway,
          persistenceService,
          contextBuilder
      );
      order.verify(modelGateway).nextStep(descriptorsOnly);
      order.verify(toolGateway).execute(call);
      order.verify(persistenceService).freezeSkill("sid", JAVA_SKILL);
      order.verify(contextBuilder).build("sid", null, null);
      order.verify(modelGateway).nextStep(withLoadedSkill);
      order.verify(persistenceService)
          .saveInitialQuestion("sid", "请介绍一次你解决并发问题的经历？");
    }

    @Test
    @DisplayName("模型持续调用工具时最多执行一次工具，并在第三个模型步骤后强制终止")
    void shouldStopAfterThirdStepWhenModelKeepsCallingTool() {
      AgentLoopState created = state(0, null, List.of(), AgentLoopStatus.CREATED);
      InterviewAgentContext descriptorsOnly = context(0, null, List.of(), null);
      InterviewAgentContext withLoadedSkill = context(0, JAVA_SKILL, List.of(), null);
      AgentStep.CallTool call = new AgentStep.CallTool(
          "load_skill",
          Map.of("skillId", "java-backend")
      );

      when(persistenceService.create("JD", "Resume", 6)).thenReturn(created);
      when(contextBuilder.build("sid", null, null))
          .thenReturn(descriptorsOnly, withLoadedSkill, withLoadedSkill);
      when(modelGateway.nextStep(any())).thenReturn(call);
      when(toolGateway.execute(call)).thenReturn(new ToolResult("load_skill", JAVA_SKILL));

      assertThatThrownBy(() -> loop.createSession("JD", "Resume"))
          .isInstanceOf(BusinessException.class)
          .extracting("code")
          .isEqualTo(ErrorCode.AGENT_INTERVIEW_DECISION_FAILED.getCode());

      verify(modelGateway, times(3)).nextStep(any());
      verify(toolGateway, times(1)).execute(call);
      verify(persistenceService, never()).saveInitialQuestion(any(), any());
      verify(persistenceService).markFailed("sid", "Agent超过单轮最大执行步骤");
    }
  }

  @Nested
  @DisplayName("提交回答")
  class SubmitAnswer {

    @Test
    @DisplayName("当前回答显式进入 Context，并原子保存回答与自适应追问")
    void shouldUseCurrentAnswerForAdaptiveFollowUp() {
      List<Turn> turns = List.of(new Turn(1, "你如何处理缓存一致性？", null));
      AgentLoopState before = state(1, JAVA_SKILL, turns, AgentLoopStatus.IN_PROGRESS);
      InterviewAgentContext answerContext = context(
          1,
          JAVA_SKILL,
          turns,
          "我使用延迟双删，并让删除操作可重试",
          ASSESSMENT
      );
      AssessmentContext assessmentContext = assessmentContext(
          "你如何处理缓存一致性？",
          "我使用延迟双删，并让删除操作可重试",
          List.of()
      );
      AgentLoopState after = state(
          2,
          JAVA_SKILL,
          List.of(
              new Turn(1, "你如何处理缓存一致性？", "我使用延迟双删，并让删除操作可重试"),
              new Turn(2, "如果第二次删除失败，你如何保证最终一致性？", null)
          ),
          AgentLoopStatus.IN_PROGRESS
      );

      when(persistenceService.get("sid")).thenReturn(before, after);
      when(contextBuilder.buildAssessment(
          "sid",
          "我使用延迟双删，并让删除操作可重试"
      )).thenReturn(assessmentContext);
      when(modelGateway.assess(assessmentContext)).thenReturn(ASSESSMENT);
      when(contextBuilder.build(
          "sid",
          "我使用延迟双删，并让删除操作可重试",
          ASSESSMENT
      ))
          .thenReturn(answerContext);
      when(modelGateway.nextStep(answerContext))
          .thenReturn(new AgentStep.Ask("如果第二次删除失败，你如何保证最终一致性？"));

      AgentLoopState result = loop.submitAnswer(
          "sid",
          "我使用延迟双删，并让删除操作可重试"
      );

      assertThat(result.currentTurn()).isEqualTo(2);
      verify(persistenceService).saveAnswerAndQuestion(
          "sid",
          1,
          "我使用延迟双删，并让删除操作可重试",
          AnswerDepthLevel.L3,
          ASSESSMENT.evidence(),
          "如果第二次删除失败，你如何保证最终一致性？"
      );
    }

    @Test
    @DisplayName("第六轮回答后模型结束会话，不再生成第七题")
    void shouldFinishAfterSixthAnswer() {
      List<Turn> turns = List.of(
          new Turn(1, "问题1？", "回答1"),
          new Turn(2, "问题2？", "回答2"),
          new Turn(3, "问题3？", "回答3"),
          new Turn(4, "问题4？", "回答4"),
          new Turn(5, "问题5？", "回答5"),
          new Turn(6, "问题6？", null)
      );
      AgentLoopState before = state(6, JAVA_SKILL, turns, AgentLoopStatus.IN_PROGRESS);
      AssessmentResult sixthAssessment = new AssessmentResult(
          AnswerDepthLevel.L1,
          new AnswerEvidence("回答较浅", "回答6"),
          AssessmentAction.FINISH
      );
      AssessmentContext assessmentContext = assessmentContext("问题6？", "回答6", List.of());
      InterviewAgentContext answerContext = context(
          6,
          JAVA_SKILL,
          turns,
          "回答6",
          sixthAssessment
      );
      AgentLoopState after = state(
          6,
          JAVA_SKILL,
          List.of(
              new Turn(1, "问题1？", "回答1"),
              new Turn(2, "问题2？", "回答2"),
              new Turn(3, "问题3？", "回答3"),
              new Turn(4, "问题4？", "回答4"),
              new Turn(5, "问题5？", "回答5"),
              new Turn(6, "问题6？", "回答6")
          ),
          AgentLoopStatus.COMPLETED
      );

      when(persistenceService.get("sid")).thenReturn(before, after);
      when(contextBuilder.buildAssessment("sid", "回答6")).thenReturn(assessmentContext);
      when(modelGateway.assess(assessmentContext)).thenReturn(sixthAssessment);
      when(contextBuilder.build("sid", "回答6", sixthAssessment)).thenReturn(answerContext);
      when(modelGateway.nextStep(answerContext))
          .thenReturn(new AgentStep.Finish("已完成六轮面试"));

      AgentLoopState result = loop.submitAnswer("sid", "回答6");

      assertThat(result.status()).isEqualTo(AgentLoopStatus.COMPLETED);
      verify(persistenceService)
          .saveAnswerAndFinish(
              "sid",
              6,
              "回答6",
              AnswerDepthLevel.L1,
              sixthAssessment.evidence(),
              "已完成六轮面试"
          );
      verify(persistenceService, never()).saveAnswerAndQuestion(
          any(),
          any(Integer.class),
          any(),
          any(),
          any(),
          any()
      );
    }

    @Test
    @DisplayName("虚构引用被丢弃，但评级和面试流程继续推进")
    void shouldDiscardFabricatedQuoteAndContinue() {
      List<Turn> turns = List.of(new Turn(1, "请说明事务边界？", null));
      AgentLoopState before = state(1, JAVA_SKILL, turns, AgentLoopStatus.IN_PROGRESS);
      AssessmentContext assessmentContext = assessmentContext(
          "请说明事务边界？",
          "外部调用不应放在数据库事务内",
          List.of()
      );
      AssessmentResult fabricated = new AssessmentResult(
          AnswerDepthLevel.L2,
          new AnswerEvidence("说明了事务边界", "我使用了两阶段提交"),
          AssessmentAction.FOLLOW_UP
      );
      AssessmentResult sanitized = new AssessmentResult(
          AnswerDepthLevel.L2,
          null,
          AssessmentAction.FOLLOW_UP
      );
      InterviewAgentContext answerContext = context(
          1,
          JAVA_SKILL,
          turns,
          "外部调用不应放在数据库事务内",
          sanitized
      );

      when(persistenceService.get("sid")).thenReturn(before, before);
      when(contextBuilder.buildAssessment(
          "sid",
          "外部调用不应放在数据库事务内"
      )).thenReturn(assessmentContext);
      when(modelGateway.assess(assessmentContext)).thenReturn(fabricated);
      when(contextBuilder.build(
          "sid",
          "外部调用不应放在数据库事务内",
          sanitized
      )).thenReturn(answerContext);
      when(modelGateway.nextStep(answerContext))
          .thenReturn(new AgentStep.Ask("如果调用超时，你会如何恢复？"));

      loop.submitAnswer("sid", "外部调用不应放在数据库事务内");

      verify(persistenceService).saveAnswerAndQuestion(
          "sid",
          1,
          "外部调用不应放在数据库事务内",
          AnswerDepthLevel.L2,
          null,
          "如果调用超时，你会如何恢复？"
      );
    }

    @Test
    @DisplayName("评估失败时不保存回答且允许原答案重试")
    void shouldNotAdvanceWhenAssessmentFails() {
      List<Turn> turns = List.of(new Turn(1, "请说明事务边界？", null));
      AgentLoopState before = state(1, JAVA_SKILL, turns, AgentLoopStatus.IN_PROGRESS);
      AssessmentContext assessmentContext = assessmentContext(
          "请说明事务边界？",
          "外部调用不应放在数据库事务内",
          List.of()
      );
      when(persistenceService.get("sid")).thenReturn(before);
      when(contextBuilder.buildAssessment(
          "sid",
          "外部调用不应放在数据库事务内"
      )).thenReturn(assessmentContext);
      when(modelGateway.assess(assessmentContext)).thenThrow(
          new BusinessException(ErrorCode.AGENT_INTERVIEW_DECISION_FAILED, "评估失败")
      );

      assertThatThrownBy(() -> loop.submitAnswer(
          "sid",
          "外部调用不应放在数据库事务内"
      )).isInstanceOf(BusinessException.class)
          .hasMessageContaining("评估失败");

      verify(persistenceService, never()).saveAnswerAndQuestion(
          any(),
          any(Integer.class),
          any(),
          any(),
          any(),
          any()
      );
      verify(persistenceService, never()).saveAnswerAndFinish(
          any(),
          any(Integer.class),
          any(),
          any(),
          any(),
          any()
      );
      verify(modelGateway, never()).nextStep(any());
    }

    @Test
    @DisplayName("评估超过独立时限时终止本轮且不保存回答")
    void shouldStopAssessmentAtItsDeadline() {
      runtimeProperties.setAssessmentTimeout(Duration.ofMillis(30));
      List<Turn> turns = List.of(new Turn(1, "请说明事务边界？", null));
      AgentLoopState before = state(1, JAVA_SKILL, turns, AgentLoopStatus.IN_PROGRESS);
      AssessmentContext assessmentContext = assessmentContext(
          "请说明事务边界？",
          "外部调用不应放在数据库事务内",
          List.of()
      );
      when(persistenceService.get("sid")).thenReturn(before);
      when(contextBuilder.buildAssessment(
          "sid",
          "外部调用不应放在数据库事务内"
      )).thenReturn(assessmentContext);
      when(modelGateway.assess(assessmentContext)).thenAnswer(invocation -> {
        Thread.sleep(5_000);
        return ASSESSMENT;
      });

      assertThatThrownBy(() -> loop.submitAnswer(
          "sid",
          "外部调用不应放在数据库事务内"
      )).isInstanceOf(BusinessException.class)
          .extracting("code")
          .isEqualTo(ErrorCode.AGENT_INTERVIEW_DEADLINE_EXCEEDED.getCode());

      verify(persistenceService, never()).saveAnswerAndQuestion(
          any(),
          any(Integer.class),
          any(),
          any(),
          any(),
          any()
      );
    }
  }

  private AgentLoopState state(
      int currentTurn,
      LoadedSkill loadedSkill,
      List<Turn> turns,
      AgentLoopStatus status
  ) {
    return new AgentLoopState(
        "sid",
        InterviewAgentLoop.RUNTIME_VERSION,
        "JD",
        "Resume",
        currentTurn,
        6,
        loadedSkill,
        turns,
        status,
        status == AgentLoopStatus.COMPLETED ? "已完成" : null
    );
  }

  private InterviewAgentContext context(
      int currentTurn,
      LoadedSkill loadedSkill,
      List<Turn> turns,
      String currentAnswer
  ) {
    return context(currentTurn, loadedSkill, turns, currentAnswer, null);
  }

  private InterviewAgentContext context(
      int currentTurn,
      LoadedSkill loadedSkill,
      List<Turn> turns,
      String currentAnswer,
      AssessmentResult currentAssessment
  ) {
    return new InterviewAgentContext(
        "JD",
        "Resume",
        currentTurn,
        6,
        List.of(new SkillDescriptor("java-backend", "Java 后端", "Java 后端岗位面试")),
        loadedSkill,
        turns.stream()
            .map(turn -> new InterviewTranscriptTurn(
                turn.turnNumber(),
                turn.question(),
                turn.answer()
            ))
            .toList(),
        currentAnswer,
        currentAssessment
    );
  }

  private AssessmentContext assessmentContext(
      String question,
      String answer,
      List<InterviewTranscriptTurn> previousTurns
  ) {
    return new AssessmentContext(question, answer, previousTurns);
  }
}
