package interview.guide.modules.interview.agent.runtime;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.tool.InterviewToolGateway;
import interview.guide.modules.interview.agent.tool.ToolResult;
import interview.guide.modules.interview.agent.runtime.AgentInterviewPersistenceService.CreateAgentSessionCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Agent 面试主循环服务，负责创建会话、提交回答、有界执行模型决策并持久化结果。
 */
@Service
@RequiredArgsConstructor
public class InterviewAgentLoop {

  public static final String RUNTIME_VERSION = "agent-loop-v2";
  public static final int MAX_MODEL_STEPS = 3;
  public static final int MAX_TOOL_CALLS = 1;
  public static final int MAX_TURNS = 6;

  private static final int MAX_QUESTION_LENGTH = 500;

  private final AgentModelGateway modelGateway;
  private final AgentContextBuilder contextBuilder;
  private final InterviewToolGateway toolGateway;
  private final AgentInterviewPersistenceService persistenceService;
  private final AgentInterviewRuntimeProperties runtimeProperties;

  /**
   * 创建新的 Agent 面试会话，并让模型生成首轮问题。
   *
   * @param jd 职位描述
   * @param resume 候选人简历
   * @return 创建后的会话状态
   */
  public AgentLoopState createSession(UUID candidateId, String jd, String resume) {
    AgentLoopState created = persistenceService.create(
        new CreateAgentSessionCommand(candidateId, jd, resume, MAX_TURNS));
    try {
      TerminalStep terminal = runBounded(
          candidateId,
          created.sessionId(),
          null,
          null,
          new RunBudget(deadlineFromNow(runtimeProperties.getDeadline()))
      );

      switch (terminal.step()) {
        case AgentStep.Ask ask -> persistenceService.saveInitialQuestion(
            created.sessionId(), ask.question());
        case AgentStep.Finish finish -> persistenceService.finishBeforeFirstQuestion(
            created.sessionId(), finish.reason());
        case AgentStep.CallTool ignored -> throw decisionFailed("Agent Loop 未产生终止动作");
      }
      return persistenceService.get(created.sessionId());
    } catch (BusinessException e) {
      persistenceService.markFailed(created.sessionId(), e.getMessage());
      throw e;
    }
  }

  /**
   * 提交候选人回答：先做深度评估，再让 Agent 决定追问、结束或继续。
   *
   * @param sessionId 会话 ID
   * @param answer 候选人回答原文
   * @return 推进后的会话状态
   */
  public AgentLoopState submitAnswer(UUID candidateId, String sessionId, String answer) {
    AgentLoopState snapshot = persistenceService.get(candidateId, sessionId);
    if (snapshot.status() == AgentLoopStatus.COMPLETED) {
      throw new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED);
    }
    if (snapshot.currentTurn() <= 0 || snapshot.currentQuestion() == null) {
      throw decisionFailed("当前会话没有待回答的问题");
    }

    long totalDeadlineNanos = deadlineFromNow(runtimeProperties.getDeadline());
    AssessmentContext assessmentContext = contextBuilder.buildAssessment(sessionId, answer);
    AssessmentResult assessment = validateEvidence(
        assessmentWithinDeadline(candidateId, assessmentContext, totalDeadlineNanos),
        answer
    );
    long decisionDeadlineNanos = Math.min(
        totalDeadlineNanos,
        deadlineFromNow(runtimeProperties.getDecisionTimeout())
    );
    TerminalStep terminal = runBounded(
        candidateId,
        sessionId,
        answer,
        assessment,
        new RunBudget(decisionDeadlineNanos)
    );
    switch (terminal.step()) {
      case AgentStep.Ask ask -> {
        if (snapshot.currentTurn() >= snapshot.maxTurns()) {
          throw decisionFailed("达到最大轮次后 Agent 必须返回 FINISH");
        }
        persistenceService.saveAnswerAndQuestion(
            sessionId,
            snapshot.currentTurn(),
            answer,
            assessment.depth(),
            assessment.evidence(),
            ask.question()
        );
      }
      case AgentStep.Finish finish -> persistenceService.saveAnswerAndFinish(
          sessionId,
          snapshot.currentTurn(),
          answer,
          assessment.depth(),
          assessment.evidence(),
          finish.reason()
      );
      case AgentStep.CallTool ignored -> throw decisionFailed("Agent Loop 未产生终止动作");
    }
    return persistenceService.get(sessionId);
  }

  /**
   * 获取指定会话的最新状态。
   *
   * @param sessionId 会话 ID
   * @return 当前会话状态
   */
  public AgentLoopState getSession(UUID candidateId, String sessionId) {
    return persistenceService.get(candidateId, sessionId);
  }

  private TerminalStep runBounded(
      UUID candidateId,
      String sessionId,
      String currentAnswer,
      AssessmentResult currentAssessment,
      RunBudget initialBudget
  ) {
    RunBudget budget = initialBudget;
    for (int step = 0; step < MAX_MODEL_STEPS; step++) {
      InterviewAgentContext context = contextBuilder.build(
          sessionId,
          currentAnswer,
          currentAssessment
      );
      AgentStep next = nextStepWithinDeadline(
          candidateId, context, budget.deadlineNanos());
      switch (next) {
        case AgentStep.CallTool call -> {
          if (context.loadedSkill() != null || budget.toolCalls() >= MAX_TOOL_CALLS) {
            continue;
          }
          ToolResult result = toolGateway.execute(call);
          persistenceService.freezeSkill(sessionId, result.loadedSkill());
          budget = budget.withToolCall();
        }
        case AgentStep.Ask ask -> {
          if (context.loadedSkill() == null) {
            throw decisionFailed("Agent 必须先调用 load_skill 再生成问题");
          }
          validateSingleQuestion(ask.question());
          return new TerminalStep(ask);
        }
        case AgentStep.Finish finish -> {
          return new TerminalStep(finish);
        }
      }
    }

    throw decisionFailed("Agent超过单轮最大执行步骤");
  }

  private AgentStep nextStepWithinDeadline(
      UUID candidateId,
      InterviewAgentContext context,
      long deadlineNanos
  ) {
    return executeWithinDeadline(
        () -> modelGateway.nextStep(candidateId, context),
        deadlineNanos,
        "agent-interview-model-step",
        "Agent 单轮决策"
    );
  }

  private AssessmentResult assessmentWithinDeadline(
      UUID candidateId,
      AssessmentContext context,
      long totalDeadlineNanos
  ) {
    long assessmentDeadlineNanos = Math.min(
        totalDeadlineNanos,
        deadlineFromNow(runtimeProperties.getAssessmentTimeout())
    );
    return executeWithinDeadline(
        () -> modelGateway.assess(candidateId, context),
        assessmentDeadlineNanos,
        "agent-interview-assessment",
        "Agent 回答评估"
    );
  }

  private <T> T executeWithinDeadline(
      Callable<T> operation,
      long deadlineNanos,
      String threadName,
      String operationName
  ) {
    long remainingNanos = deadlineNanos - System.nanoTime();
    if (remainingNanos <= 0) {
      throw deadlineExceeded(operationName, null);
    }

    FutureTask<T> task = new FutureTask<>(operation);
    Thread worker = Thread.ofVirtual().name(threadName).start(task);
    try {
      return task.get(remainingNanos, TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      task.cancel(true);
      worker.interrupt();
      throw deadlineExceeded(operationName, e);
    } catch (InterruptedException e) {
      task.cancel(true);
      Thread.currentThread().interrupt();
      throw new BusinessException(
          ErrorCode.AGENT_INTERVIEW_DECISION_FAILED,
          operationName + "被中断",
          e
      );
    } catch (ExecutionException e) {
      if (e.getCause() instanceof BusinessException businessException) {
        throw businessException;
      }
      throw new BusinessException(
          ErrorCode.AGENT_INTERVIEW_DECISION_FAILED,
          operationName + "失败",
          e.getCause()
      );
    }
  }

  private AssessmentResult validateEvidence(AssessmentResult assessment, String answer) {
    if (assessment == null
        || assessment.depth() == null
        || assessment.suggestedAction() == null) {
      throw decisionFailed("Agent 返回了不完整的回答评估");
    }
    AnswerEvidence evidence = assessment.evidence();
    if (evidence == null) {
      return assessment;
    }
    if (evidence.finding() == null
        || evidence.finding().isBlank()
        || evidence.quote() == null
        || evidence.quote().isBlank()
        || !answer.contains(evidence.quote())) {
      return new AssessmentResult(
          assessment.depth(),
          null,
          assessment.suggestedAction()
      );
    }
    return assessment;
  }

  private long deadlineFromNow(Duration duration) {
    if (duration == null || duration.isZero() || duration.isNegative()) {
      throw decisionFailed("Agent 超时配置必须大于零");
    }
    return System.nanoTime() + duration.toNanos();
  }

  private BusinessException deadlineExceeded(String operationName, Throwable cause) {
    String message = operationName + "超过时限";
    return cause == null
        ? new BusinessException(ErrorCode.AGENT_INTERVIEW_DEADLINE_EXCEEDED, message)
        : new BusinessException(
            ErrorCode.AGENT_INTERVIEW_DEADLINE_EXCEEDED,
            message,
            cause
        );
  }

  private void validateSingleQuestion(String question) {
    if (question == null || question.isBlank()) {
      throw decisionFailed("Agent 返回了空问题");
    }
    String normalized = question.trim();
    if (normalized.length() > MAX_QUESTION_LENGTH) {
      throw decisionFailed("Agent 返回的问题过长");
    }
    if (normalized.lines().filter(line -> !line.isBlank()).count() != 1) {
      throw decisionFailed("Agent 必须一次只返回一个问题");
    }
    long questionMarks = normalized.chars()
        .filter(ch -> ch == '?' || ch == '？')
        .count();
    if (questionMarks != 1) {
      throw decisionFailed("Agent 必须返回一个明确的单问题");
    }
  }

  private BusinessException decisionFailed(String message) {
    return new BusinessException(ErrorCode.AGENT_INTERVIEW_DECISION_FAILED, message);
  }

  private record RunBudget(long deadlineNanos, int toolCalls) {

    RunBudget(long deadlineNanos) {
      this(deadlineNanos, 0);
    }

    RunBudget withToolCall() {
      return new RunBudget(deadlineNanos, toolCalls + 1);
    }
  }

  private record TerminalStep(AgentStep step) {}
}
