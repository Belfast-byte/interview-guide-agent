package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageProjector;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.core.session.AdoptedRubricSource;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionIdentityFactory;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionPublication;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.QuestionExposurePersistence;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanEntity;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 原子保存创建计划、首题及其来源；模型调用在事务外完成。 */
@Service
public class AdaptiveCreationTransactionService {

  private final AdaptiveCreationRepositories repositories;
  private final QuestionExposurePersistence exposurePersistence;
  private final QuestionIdentityFactory identityFactory;

  private final RubricSnapshotResolver rubricSnapshots;
  private final interview.guide.modules.interview.agent.adaptive.persistence.memory.JpaMemoryEvidenceService memoryEvidence;

  public AdaptiveCreationTransactionService(
      AdaptiveCreationRepositories repositories,
      QuestionExposurePersistence exposurePersistence,
      QuestionIdentityFactory identityFactory,
      RubricSnapshotResolver rubricSnapshots,
      interview.guide.modules.interview.agent.adaptive.persistence.memory.JpaMemoryEvidenceService memoryEvidence
  ) {
    this.repositories = repositories;
    this.exposurePersistence = exposurePersistence;
    this.identityFactory = identityFactory;
    this.rubricSnapshots = rubricSnapshots;
    this.memoryEvidence = memoryEvidence;
  }

  @Transactional
  public void create(AdaptiveSessionCreation creation, InterviewPlan plan, AgentDecision decision) {
    initialize(creation, plan);
    publishFirstTurn(new InitialTurnCommit(creation.sessionId(), plan, decision));
  }

  @Transactional
  public void initialize(AdaptiveSessionCreation creation, InterviewPlan plan) {
    if (repositories.session(creation.sessionId()).isPresent()) {
      return;
    }
    AdaptiveInterviewSession session = AdaptiveInterviewSession.create(
        creation.sessionId(), plan.maxTurns(), creation.settings());
    repositories.saveSession(new AdaptiveAgentSessionEntity(session, creation));
    repositories.savePlans(plan.dimensions().stream()
        .map(dimension -> new AdaptiveAgentPlanEntity(plan.sessionId(), dimension))
        .toList());
  }

  @Transactional
  public void publishFirstTurn(InitialTurnCommit commit) {
    if (repositories.turn(commit.sessionId(), 1).isPresent()) {
      return;
    }
    AdaptiveAgentSessionEntity session = repositories.session(commit.sessionId())
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND, "Agent 面试会话不存在"));
    AgentDecision.Ask ask = requireAsk(commit.decision());
    PlannedDimension target = target(commit.plan(), ask.targetId());
    RespondAction action = RespondAction.ask(
        ask.question().content(), ask.question().decisionSummary());
    session.apply(session.toDomain().start());
    AdaptiveAgentTurnEntity turn = repositories.saveTurn(
        new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(
            commit.sessionId(),
            1,
            target.order(),
            action,
            TurnProvenance.initial(),
            commit.decision().workingMemory(),
            rubricSnapshots.resolve(ask.question().adoptedSourceRefs().stream()
                .filter(ref -> ref.startsWith("rubric:")).toList())
        ))
    );
    turn.recordMemorySources(memoryEvidence.adopt(
        new interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner(session.tenantId(),session.candidateId()),
        target.topic(),ask.question().adoptedSourceRefs()));
    exposurePersistence.save(session, turn, new QuestionPublication(
        action, identityFactory.create(target.target(), action), null, null));
  }

  private AgentDecision.Ask requireAsk(AgentDecision decision) {
    if (decision.action() instanceof AgentDecision.Ask ask) {
      return ask;
    }
    throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "首轮 Agent 决策必须为 ASK");
  }

  private PlannedDimension target(InterviewPlan plan, String targetId) {
    return plan.dimensions().stream()
        .filter(dimension -> CoverageProjector.targetId(dimension.order()).equals(targetId))
        .findFirst()
        .orElseThrow(() -> new BusinessException(
            ErrorCode.AI_SERVICE_ERROR, "首轮 Target 不属于 Plan"));
  }

  public record InitialTurnCommit(
      String sessionId,
      InterviewPlan plan,
      AgentDecision decision
  ) {}
}
