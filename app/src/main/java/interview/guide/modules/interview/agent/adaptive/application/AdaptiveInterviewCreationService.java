package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageFacts;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageProjector;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveCreationTransactionService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveCreationTransactionService.InitialTurnCommit;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import interview.guide.modules.interview.agent.adaptive.runtime.InterviewAgentLoop;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

/** 规划完成后的创建编排；模型调用始终位于两个短事务之外。 */
@Service
public class AdaptiveInterviewCreationService {

  private final AdaptiveCreationTransactionService transactions;
  private final InterviewAgentLoop agentLoop;
  private final AdaptiveInterviewPersistenceService persistenceService;

  public AdaptiveInterviewCreationService(
      AdaptiveCreationTransactionService transactions,
      InterviewAgentLoop agentLoop,
      AdaptiveInterviewPersistenceService persistenceService
  ) {
    this.transactions = transactions;
    this.agentLoop = agentLoop;
    this.persistenceService = persistenceService;
  }

  public PlannedInterview initialize(InitialAgentRun run) {
    transactions.initialize(run.creation(), run.plan());
    return persistenceService.get(run.creation().sessionId());
  }

  public PlannedInterview complete(InitialAgentRun run) {
    AgentDecision decision = agentLoop.run(context(run), run.deadline());
    if (!(decision.action() instanceof AgentDecision.Ask)) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "首轮 Agent 决策必须为 ASK");
    }
    transactions.publishFirstTurn(new InitialTurnCommit(
        run.creation().sessionId(), run.plan(), decision));
    return persistenceService.get(run.creation().sessionId());
  }

  private AgentContext context(InitialAgentRun run) {
    InterviewPlan plan = run.plan();
    var coverage = CoverageProjector.project(new CoverageFacts(
        plan.maxTurns(),
        plan.dimensions().stream().map(PlannedDimension::target).toList(),
        List.of(),
        List.of(),
        List.of(),
        List.of()
    ));
    return new AgentContext(
        new AgentContext.SessionWindow(
            new AgentContext.SessionIdentity(
                run.creation().sessionId(), run.creation().llmProviderId()),
            run.creation().settings().mode(),
            plan.maxTurns()
        ),
        new AgentContext.Facts(coverage, List.of(), List.of()),
        WorkingMemory.empty()
    );
  }

  public record InitialAgentRun(
      AdaptiveSessionCreation creation,
      InterviewPlan plan,
      Duration deadline
  ) {}
}
