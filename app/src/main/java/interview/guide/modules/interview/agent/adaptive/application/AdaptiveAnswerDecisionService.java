package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import interview.guide.modules.interview.agent.adaptive.runtime.InterviewAgentLoop;
import java.time.Duration;
import org.springframework.stereotype.Service;

/** 事务外执行 Assessor 与 InterviewAgentLoop；达到 maxTurns 时直接结束。 */
@Service
public class AdaptiveAnswerDecisionService {

  private final AdaptiveAnswerPreparationService preparation;
  private final AnswerAgentContextFactory contextFactory;
  private final InterviewAgentLoop agentLoop;

  public AdaptiveAnswerDecisionService(
      AdaptiveAnswerPreparationService preparation,
      AnswerAgentContextFactory contextFactory,
      InterviewAgentLoop agentLoop
  ) {
    this.preparation = preparation;
    this.contextFactory = contextFactory;
    this.agentLoop = agentLoop;
  }

  public AnswerProgressionDecision decide(AnswerDecisionRequest request) {
    AnswerAssessment assessment = preparation.prepare(request.interview(), request.answer());
    var context = contextFactory.create(request.interview(), request.answer(), assessment);
    AgentDecision decision = request.interview().coverage().remainingTurns() == 0
        ? new AgentDecision(
            context.workingMemory(),
            new AgentDecision.Finish("已达到本场最大轮次")
        )
        : agentLoop.run(context, request.deadline());
    return new AnswerProgressionDecision(assessment, decision);
  }

  public record AnswerDecisionRequest(
      PlannedInterview interview,
      CandidateAnswer answer,
      Duration deadline
  ) {}
}
