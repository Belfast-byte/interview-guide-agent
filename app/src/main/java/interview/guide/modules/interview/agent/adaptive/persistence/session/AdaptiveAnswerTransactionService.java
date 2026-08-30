package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AnswerAssessment;
import interview.guide.modules.interview.agent.adaptive.application.AnswerProgressionDecision;
import interview.guide.modules.interview.agent.adaptive.application.PendingAssessmentReferences;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageProjector;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.core.session.AdoptedRubricSource;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAnswerSideEffects.EpisodeAssessment;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAnswerSideEffects.QuestionExposureInput;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAnswerSideEffects.QuestionTarget;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 以 Session/Turn 锁一次提交回答的全部正式事实与下一 Turn。 */
@Service
public class AdaptiveAnswerTransactionService {

  private final AdaptiveAnswerCoreRepositories core;
  private final AdaptiveAssessmentRepositories assessments;
  private final AdaptiveAnswerSideEffects sideEffects;

  public AdaptiveAnswerTransactionService(
      AdaptiveAnswerCoreRepositories core,
      AdaptiveAssessmentRepositories assessments,
      AdaptiveAnswerSideEffects sideEffects
  ) {
    this.core = core;
    this.assessments = assessments;
    this.sideEffects = sideEffects;
  }

  @Transactional
  public void commit(AnswerCommit commit) {
    String sessionId = commit.interview().history().session().id();
    CandidateAnswer answer = commit.facts().answer();
    AdaptiveAgentSessionEntity session = lockedSession(sessionId, commit.owner());
    if (assessments.assessment(sessionId, answer.turnIndex()).isPresent()) {
      return;
    }
    session.toDomain().assertCanAnswer(answer);
    AdaptiveAgentTurnEntity answeredTurn = lockedAnsweredTurn(sessionId, answer);
    SavedAssessment saved = saveAssessmentFacts(
        sessionId, answer, commit.facts().progression());
    saveCodeFact(saved.assessment(), answeredTurn, sessionId);
    AnswerAssessment assessment = commit.facts().progression().assessment();
    String answeredTargetId = CoverageProjector.targetId(assessment.dimension().order());
    sideEffects.saveEpisode(session, answeredTurn, new EpisodeAssessment(
        saved.assessment(), assessment.dimension(), answeredTargetId));
    applyDecision(new DecisionCommit(
        commit,
        new LockedFacts(session, answeredTurn),
        saved
    ));
  }

  private AdaptiveAgentSessionEntity lockedSession(String sessionId, MemoryOwner owner) {
    AdaptiveAgentSessionEntity session = core.lockedSession(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND, "Agent 面试会话不存在"));
    if (!Objects.equals(session.tenantId(), owner.tenantId())
        || !session.candidateId().equals(owner.candidateId())) {
      throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND, "Agent 面试会话不存在");
    }
    return session;
  }

  private AdaptiveAgentTurnEntity lockedAnsweredTurn(
      String sessionId,
      CandidateAnswer answer
  ) {
    AdaptiveAgentTurnEntity turn = core.lockedTurn(sessionId, answer.turnIndex())
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "面试轮次不存在"));
    if (!turn.candidateAnswer().equals(answer)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "回答 claim 与提交事实不一致");
    }
    return turn;
  }

  private SavedAssessment saveAssessmentFacts(
      String sessionId,
      CandidateAnswer answer,
      AnswerProgressionDecision progression
  ) {
    AnswerAssessment proposed = progression.assessment();
    AdaptiveAgentAssessmentEntity assessment = assessments.saveAssessment(
        new AdaptiveAgentAssessmentEntity(
            proposed.dimension().order(),
            proposed.decision(),
            progression.targetBudgetExhausted()
        ));
    List<AssessmentProbeGapEntity> gaps = new ArrayList<>();
    for (int index = 0; index < proposed.decision().probeGaps().size(); index++) {
      gaps.add(new AssessmentProbeGapEntity(
          assessment, index + 1, proposed.decision().probeGaps().get(index)));
    }
    List<AdaptiveAgentEvidenceEntity> evidences = proposed.evidences().stream()
        .map(evidence -> new AdaptiveAgentEvidenceEntity(
            assessment, sessionId, answer.turnIndex(), evidence))
        .toList();
    List<AssessmentProbeGapEntity> savedGaps = assessments.saveGaps(gaps);
    if (progression.targetBudgetExhausted()) {
      assessments.closeOpenGaps(
          sessionId, proposed.dimension().order(), assessment);
    }
    return new SavedAssessment(
        assessment,
        savedGaps,
        assessments.saveEvidences(evidences)
    );
  }

  private void saveCodeFact(
      AdaptiveAgentAssessmentEntity assessment,
      AdaptiveAgentTurnEntity turn,
      String sessionId
  ) {
    AdaptiveAgentEvidenceEntity.codeFact(
        assessment,
        sessionId,
        turn.turnIndex(),
        turn.codeSourceId(),
        turn.codeAnchor(),
        turn.codeFactUsage()
    ).ifPresent(evidence -> assessments.saveEvidences(List.of(evidence)));
  }

  private void applyDecision(DecisionCommit commit) {
    AgentDecision decision = commit.commit().facts().progression().agentDecision();
    CandidateAnswer answer = commit.commit().facts().answer();
    RespondAction action = response(decision);
    commit.locked().answeredTurn().recordResponse(action);
    commit.locked().session().apply(
        commit.locked().session().toDomain().apply(answer, action).session());
    if (!(decision.action() instanceof AgentDecision.Ask ask)) {
      return;
    }
    publishNextTurn(commit, ask, action);
  }

  private void publishNextTurn(
      DecisionCommit commit,
      AgentDecision.Ask ask,
      RespondAction action
  ) {
    Map<Long, Long> gapIds = gapIds(commit.saved().gaps());
    Map<Long, Long> evidenceIds = evidenceIds(commit.saved().evidences());
    WorkingMemory memory = PendingFactReferenceResolver.resolve(
        commit.commit().facts().progression().agentDecision().workingMemory(),
        gapIds,
        evidenceIds
    );
    CandidateAnswer answer = commit.commit().facts().answer();
    PlannedInterview interview = commit.commit().interview();
    PlannedDimension target = target(interview.plan(), ask.targetId());
    TurnProvenance provenance = provenance(commit, ask, gapIds);
    AdaptiveAgentTurnEntity nextTurn = core.saveTurn(
        new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(
            interview.history().session().id(),
            answer.turnIndex() + 1,
            target.order(),
            action,
            provenance,
            memory,
            ask.question().adoptedSourceRefs().stream()
                .map(AdoptedRubricSource::fromReference)
                .toList()
        ))
    );
    sideEffects.saveExposure(new QuestionExposureInput(
        commit.locked().session(), nextTurn, new QuestionTarget(target, action)));
  }

  private RespondAction response(AgentDecision decision) {
    if (decision.action() instanceof AgentDecision.Ask ask) {
      return RespondAction.ask(ask.question().content(), ask.question().decisionSummary());
    }
    AgentDecision.Finish finish = (AgentDecision.Finish) decision.action();
    return RespondAction.finish("面试已结束。", finish.decisionSummary());
  }

  private TurnProvenance provenance(
      DecisionCommit commit,
      AgentDecision.Ask ask,
      Map<Long, Long> gapIds
  ) {
    CandidateAnswer answer = commit.commit().facts().answer();
    PlannedInterview interview = commit.commit().interview();
    Long sourceGapId = ask.sourceGapId();
    if (sourceGapId == null) {
      return TurnProvenance.agentDecision(answer.turnIndex());
    }
    long resolvedGapId = PendingFactReferenceResolver.requireId(sourceGapId, gapIds);
    long resolvedAssessmentId = PendingAssessmentReferences.pending(sourceGapId)
        ? commit.saved().assessment().id()
        : interview.coverage().openProbeGaps().stream()
            .filter(gap -> gap.gapId() == sourceGapId)
            .mapToLong(gap -> gap.assessmentId())
            .findFirst()
            .orElseThrow();
    return TurnProvenance.assessmentGap(
        answer.turnIndex(), resolvedAssessmentId, resolvedGapId);
  }

  private Map<Long, Long> gapIds(List<AssessmentProbeGapEntity> gaps) {
    Map<Long, Long> ids = new LinkedHashMap<>();
    for (int index = 0; index < gaps.size(); index++) {
      ids.put(PendingAssessmentReferences.gapId(index), gaps.get(index).id());
    }
    return ids;
  }

  private Map<Long, Long> evidenceIds(List<AdaptiveAgentEvidenceEntity> evidences) {
    Map<Long, Long> ids = new LinkedHashMap<>();
    for (int index = 0; index < evidences.size(); index++) {
      ids.put(PendingAssessmentReferences.evidenceId(index), evidences.get(index).id());
    }
    return ids;
  }

  private PlannedDimension target(InterviewPlan plan, String targetId) {
    return plan.dimensions().stream()
        .filter(target -> CoverageProjector.targetId(target.order()).equals(targetId))
        .findFirst()
        .orElseThrow(() -> new BusinessException(
            ErrorCode.AI_SERVICE_ERROR, "Agent Target 不属于 Plan"));
  }

  public record AnswerCommit(
      MemoryOwner owner,
      PlannedInterview interview,
      CommitFacts facts
  ) {}

  public record CommitFacts(
      CandidateAnswer answer,
      AnswerProgressionDecision progression
  ) {}

  private record SavedAssessment(
      AdaptiveAgentAssessmentEntity assessment,
      List<AssessmentProbeGapEntity> gaps,
      List<AdaptiveAgentEvidenceEntity> evidences
  ) {}

  private record LockedFacts(
      AdaptiveAgentSessionEntity session,
      AdaptiveAgentTurnEntity answeredTurn
  ) {}

  private record DecisionCommit(
      AnswerCommit commit,
      LockedFacts locked,
      SavedAssessment saved
  ) {}
}
