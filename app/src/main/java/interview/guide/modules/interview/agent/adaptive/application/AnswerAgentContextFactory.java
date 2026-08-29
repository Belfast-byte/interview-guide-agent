package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageProjector;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.persistence.session.WorkingMemorySnapshotReader;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** 把已持久化 Coverage 与当前 Assessor proposal 合成中性 AgentContext。 */
@Component
public class AnswerAgentContextFactory {

  private final WorkingMemorySnapshotReader snapshotReader;

  public AnswerAgentContextFactory(WorkingMemorySnapshotReader snapshotReader) {
    this.snapshotReader = snapshotReader;
  }

  public AgentContext create(
      PlannedInterview interview,
      CandidateAnswer answer,
      AnswerAssessment assessment
  ) {
    var history = interview.history();
    return new AgentContext(
        new AgentContext.SessionWindow(
            new AgentContext.SessionIdentity(history.session().id(), history.llmProvider()),
            history.session().settings().mode(),
            history.session().maxTurns()
        ),
        new AgentContext.Facts(
            coverage(interview.coverage(), answer, assessment),
            answeredTurns(history.turns(), answer),
            List.of()
        ),
        snapshotReader.latest(history.session().id())
    );
  }

  private CoverageView coverage(
      CoverageView current,
      CandidateAnswer answer,
      AnswerAssessment assessment
  ) {
    String targetId = CoverageProjector.targetId(assessment.dimension().order());
    List<CoverageView.OpenProbeGap> newGaps = new ArrayList<>(current.openProbeGaps());
    for (int index = 0; index < assessment.decision().probeGaps().size(); index++) {
      var gap = assessment.decision().probeGaps().get(index);
      newGaps.add(new CoverageView.OpenProbeGap(
          PendingAssessmentReferences.gapId(index),
          PendingAssessmentReferences.ASSESSMENT_ID,
          targetId,
          answer.turnIndex(),
          gap.anchor(),
          gap.missingPoint()
      ));
    }
    List<Long> evidenceIds = new ArrayList<>(current.evidenceIds());
    for (int index = 0; index < assessment.evidences().size(); index++) {
      evidenceIds.add(PendingAssessmentReferences.evidenceId(index));
    }
    return new CoverageView(
        current.askedTurns(),
        current.remainingTurns(),
        targetCoverage(current, new TargetProjection(targetId, assessment, newGaps)),
        newGaps,
        evidenceIds
    );
  }

  private List<CoverageView.TargetCoverage> targetCoverage(
      CoverageView current,
      TargetProjection projection
  ) {
    return current.targets().stream().map(target -> target.targetId().equals(projection.targetId())
        ? new CoverageView.TargetCoverage(
            target.targetId(),
            target.target(),
            target.askedTurns(),
            projection.assessment().decision().depthLevel(),
            projection.gaps().stream()
                .filter(gap -> gap.targetId().equals(projection.targetId()))
                .map(CoverageView.OpenProbeGap::gapId).toList(),
            evidenceIds(target, projection.assessment())
        )
        : target).toList();
  }

  private List<Long> evidenceIds(
      CoverageView.TargetCoverage target,
      AnswerAssessment assessment
  ) {
    List<Long> ids = new ArrayList<>(target.evidenceIds());
    for (int index = 0; index < assessment.evidences().size(); index++) {
      ids.add(PendingAssessmentReferences.evidenceId(index));
    }
    return ids;
  }

  private List<AdaptiveInterviewTurn> answeredTurns(
      List<AdaptiveInterviewTurn> turns,
      CandidateAnswer answer
  ) {
    return turns.stream().map(turn -> turn.turnIndex() == answer.turnIndex()
        ? new AdaptiveInterviewTurn(
            turn.turnIndex(), turn.dimensionOrder(), turn.question(), turn.questionReason(),
            answer.content(), turn.responseType(), turn.responseContent(), turn.decisionReason(),
            turn.provenance())
        : turn).toList();
  }

  private record TargetProjection(
      String targetId,
      AnswerAssessment assessment,
      List<CoverageView.OpenProbeGap> gaps
  ) {}
}
