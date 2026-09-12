package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler.AgentContextInput;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveCreationTransactionService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecisionValidator;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 校验一次创建提案，并用一个短事务保存计划与首题。 */
@Service
public class AdaptiveInterviewCreationService {

  private final AdaptiveCreationTransactionService transactions;
  private final AdaptiveInterviewPersistenceService persistenceService;
  private final ContextAssembler contextAssembler;
  private final AgentDecisionValidator validator;

  public AdaptiveInterviewCreationService(
      AdaptiveCreationTransactionService transactions,
      AdaptiveInterviewPersistenceService persistenceService,
      ContextAssembler contextAssembler,
      AgentDecisionValidator validator
  ) {
    this.transactions = transactions;
    this.persistenceService = persistenceService;
    this.contextAssembler = contextAssembler;
    this.validator = validator;
  }

  public PlannedInterview create(InitialAgentRun run) {
    validate(run);
    transactions.create(run.creation(), run.plan(), run.decision());
    return persistenceService.get(run.creation().sessionId());
  }

  private void validate(InitialAgentRun run) {
    AgentContext agentContext = context(run);
    requireValid(validator.validateMemory(run.decision(), agentContext, List.of()));
    requireValid(validator.validateAction(run.decision(), agentContext, List.of()));
  }

  private void requireValid(Optional<DecisionObservation> rejection) {
    rejection.ifPresent(value -> {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "首题提案非法: " + value.field() + ": " + value.message()
      );
    });
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
        // 首题来源由当前 owner 的规划历史提供，不从模型输出推断可信引用。
        interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory.empty()
            .withAdoptedSources(run.availableEpisodeRefs())
    ));
  }

  public record InitialAgentRun(
      AdaptiveSessionCreation creation,
      InterviewPlan plan,
      AgentDecision decision,
      List<String> availableEpisodeRefs
  ) {
    public InitialAgentRun {
      availableEpisodeRefs = List.copyOf(availableEpisodeRefs);
    }
  }
}
