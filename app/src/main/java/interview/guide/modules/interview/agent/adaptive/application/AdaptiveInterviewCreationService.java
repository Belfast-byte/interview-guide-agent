package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveCreationTransactionService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveCreationTransactionService.InitialTurnCommit;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import interview.guide.modules.interview.agent.adaptive.runtime.InterviewAgentLoop;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler.AgentContextInput;
import java.time.Duration;
import org.springframework.stereotype.Service;

/** 规划完成后的创建编排；模型调用始终位于两个短事务之外。 */
@Service
public class AdaptiveInterviewCreationService {

  private final AdaptiveCreationTransactionService transactions;
  private final InterviewAgentLoop agentLoop;
  private final AdaptiveInterviewPersistenceService persistenceService;
  private final ContextAssembler contextAssembler;

  public AdaptiveInterviewCreationService(
      AdaptiveCreationTransactionService transactions,
      InterviewAgentLoop agentLoop,
      AdaptiveInterviewPersistenceService persistenceService,
      ContextAssembler contextAssembler
  ) {
    this.transactions = transactions;
    this.agentLoop = agentLoop;
    this.persistenceService = persistenceService;
    this.contextAssembler = contextAssembler;
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
    return contextAssembler.agent(new AgentContextInput(
        new MemoryOwner(run.creation().tenantId(), run.creation().candidateId()),
        run.creation().sessionId(),
        run.creation().llmProviderId(),
        run.creation().settings().mode(),
        plan.maxTurns(),
        plan.dimensions(),
        interview.guide.modules.interview.agent.adaptive.core.context.CoverageProjector.project(
            new interview.guide.modules.interview.agent.adaptive.core.context.CoverageFacts(
                plan.maxTurns(),
                plan.dimensions().stream().map(PlannedDimension::target).toList(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of()
            )),
        java.util.List.of(),
        interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory.empty()
    ));
  }

  public record InitialAgentRun(
      AdaptiveSessionCreation creation,
      InterviewPlan plan,
      Duration deadline
  ) {}
}
