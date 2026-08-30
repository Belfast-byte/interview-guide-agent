package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.context.CoverageProjector;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler.AgentContextInput;
import interview.guide.modules.interview.agent.adaptive.persistence.session.WorkingMemorySnapshotReader;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import interview.guide.modules.interview.agent.adaptive.runtime.InterviewAgentLoop;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** 事务外执行 Assessor 与 InterviewAgentLoop；达到 maxTurns 时直接结束。 */
@Service
public class AdaptiveAnswerDecisionService {

  private final AdaptiveAnswerPreparationService preparation;
  private final ContextAssembler contextAssembler;
  private final WorkingMemorySnapshotReader snapshotReader;
  private final InterviewAgentLoop agentLoop;

  public AdaptiveAnswerDecisionService(
      AdaptiveAnswerPreparationService preparation,
      ContextAssembler contextAssembler,
      WorkingMemorySnapshotReader snapshotReader,
      InterviewAgentLoop agentLoop
  ) {
    this.preparation = preparation;
    this.contextAssembler = contextAssembler;
    this.snapshotReader = snapshotReader;
    this.agentLoop = agentLoop;
  }

  public AnswerProgressionDecision decide(AnswerDecisionRequest request) {
    AnswerAssessment assessment = preparation.prepare(request.interview(), request.answer());
    var context = context(request, assessment);
    AgentDecision decision = request.interview().coverage().remainingTurns() == 0
        ? new AgentDecision(context.workingMemory(), new AgentDecision.Finish("已达到本场最大轮次"))
        : agentLoop.run(context, request.deadline());
    return new AnswerProgressionDecision(assessment, decision);
  }

  private interview.guide.modules.interview.agent.adaptive.core.context.AgentContext context(
      AnswerDecisionRequest request,
      AnswerAssessment assessment
  ) {
    var interview = request.interview();
    var history = interview.history();
    return contextAssembler.agent(new AgentContextInput(
        request.owner(),
        history.session().id(),
        history.llmProvider(),
        history.session().settings().mode(),
        history.session().maxTurns(),
        interview.plan().dimensions(),
        coverage(interview.coverage(), request.answer(), assessment),
        answeredTurns(history.turns(), request.answer()),
        snapshotReader.latest(history.session().id())
    ));
  }

  private CoverageView coverage(
      CoverageView current,
      CandidateAnswer answer,
      AnswerAssessment assessment
  ) {
    String targetId = CoverageProjector.targetId(assessment.dimension().order());
    List<CoverageView.OpenProbeGap> gaps = new ArrayList<>(current.openProbeGaps());
    for (int index = 0; index < assessment.decision().probeGaps().size(); index++) {
      var gap = assessment.decision().probeGaps().get(index);
      gaps.add(new CoverageView.OpenProbeGap(
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
        targetCoverage(current, targetId, assessment, gaps),
        gaps,
        evidenceIds
    );
  }

  private List<CoverageView.TargetCoverage> targetCoverage(
      CoverageView current,
      String targetId,
      AnswerAssessment assessment,
      List<CoverageView.OpenProbeGap> gaps
  ) {
    return current.targets().stream().map(target -> target.targetId().equals(targetId)
        ? new CoverageView.TargetCoverage(
            target.targetId(),
            target.target(),
            target.askedTurns(),
            assessment.decision().depthLevel(),
            gaps.stream().filter(gap -> gap.targetId().equals(targetId))
                .map(CoverageView.OpenProbeGap::gapId).toList(),
            evidenceIds(target, assessment)
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

  public record AnswerDecisionRequest(
      MemoryOwner owner,
      PlannedInterview interview,
      CandidateAnswer answer,
      Duration deadline
  ) {}
}
