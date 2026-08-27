package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerContext;
import interview.guide.modules.interview.agent.adaptive.core.context.PlannerContext;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemorySnapshot;
import interview.guide.modules.interview.agent.adaptive.core.session.NextTurnProvenanceDraft;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import interview.guide.modules.interview.agent.adaptive.memory.working.NextQuestionWorkingMemoryInput;
import interview.guide.modules.interview.agent.adaptive.memory.working.ProbeGapCandidate;
import interview.guide.modules.interview.agent.adaptive.memory.working.ProbeGapSelector;
import interview.guide.modules.interview.agent.adaptive.memory.working.WorkingMemoryInput;
import interview.guide.modules.interview.agent.adaptive.memory.working.WorkingMemorySelection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 上下文装配器，为规划器、面试官、评估器等角色组装所需上下文。
 */
@Component
public class ContextAssembler {

  /**
   * JD 与简历注入上下文的最大字符数，超出部分截断并标注，控制每次调用的输入预算。
   */
  private static final int MAX_DOCUMENT_CHARS = 6_000;
  private static final String TRUNCATION_MARKER = "……[原文共 %d 字符，超出部分已截断]";

  public WorkingMemorySelection nextQuestionWorkingMemory(
      NextQuestionWorkingMemoryInput input
  ) {
    List<ProbeGapCandidate> candidates = candidates(input);
    Optional<ProbeGapCandidate> selected = ProbeGapSelector.select(
        input.currentTopic(),
        candidates,
        usedProbeGapIds(input.history())
    );
    if (selected.isEmpty()) {
      return plannedSelection(input);
    }
    return gapSelection(input, selected.orElseThrow());
  }

  private List<ProbeGapCandidate> candidates(NextQuestionWorkingMemoryInput input) {
    List<ProbeGapCandidate> candidates = new ArrayList<>(input.persistedGaps());
    for (int index = 0; index < input.currentAssessmentGaps().size(); index++) {
      candidates.add(new ProbeGapCandidate(
          Long.MAX_VALUE - index,
          null,
          input.currentTurnIndex() - 1,
          input.currentTopic(),
          index + 1,
          input.currentAssessmentGaps().get(index)
      ));
    }
    return candidates;
  }

  private Set<Long> usedProbeGapIds(List<AdaptiveInterviewTurn> history) {
    return history.stream()
        .map(turn -> turn.provenance().trigger().sourceProbeGapId())
        .filter(id -> id != null)
        .collect(Collectors.toUnmodifiableSet());
  }

  private WorkingMemorySelection plannedSelection(NextQuestionWorkingMemoryInput input) {
    WorkingMemorySnapshot snapshot = workingMemory(new WorkingMemoryInput(
        input.sessionId(),
        input.currentTurnIndex(),
        input.currentTopic(),
        null,
        TurnTriggerType.PLANNED,
        List.of(),
        input.history()
    ));
    return new WorkingMemorySelection(snapshot, NextTurnProvenanceDraft.planned());
  }

  private WorkingMemorySelection gapSelection(
      NextQuestionWorkingMemoryInput input,
      ProbeGapCandidate selected
  ) {
    WorkingMemorySnapshot snapshot = workingMemory(new WorkingMemoryInput(
        input.sessionId(),
        input.currentTurnIndex(),
        input.currentTopic(),
        selected.sourceTurnIndex(),
        TurnTriggerType.ASSESSMENT_GAP,
        List.of(selected.gap()),
        input.history()
    ));
    NextTurnProvenanceDraft provenance = selected.assessmentId() == null
        ? NextTurnProvenanceDraft.currentAssessmentGap(
            selected.sourceTurnIndex(),
            selected.gapOrder()
        )
        : NextTurnProvenanceDraft.persistedAssessmentGap(
            selected.sourceTurnIndex(),
            selected.assessmentId(),
            selected.id()
        );
    return new WorkingMemorySelection(snapshot, provenance);
  }

  public WorkingMemorySnapshot workingMemory(WorkingMemoryInput input) {
    validateWorkingTrigger(input);
    ProbeGap selectedGap = selectGap(input);
    int followUpDepth = followUpDepth(input.parentTurnIndex(), input.history());
    return new WorkingMemorySnapshot(
        input.sessionId(),
        input.currentTurnIndex(),
        input.currentTopic(),
        selectedGap,
        followUpDepth,
        input.triggerType()
    );
  }

  private void validateWorkingTrigger(WorkingMemoryInput input) {
    boolean planned = input.triggerType() == TurnTriggerType.PLANNED;
    if (planned != (input.parentTurnIndex() == null)) {
      throw new IllegalArgumentException("Working trigger 与父轮次不匹配");
    }
    if (input.parentTurnIndex() != null
        && input.parentTurnIndex() >= input.currentTurnIndex()) {
      throw new IllegalArgumentException("Working 父轮次必须早于当前轮次");
    }
  }

  private ProbeGap selectGap(WorkingMemoryInput input) {
    if (input.triggerType() != TurnTriggerType.ASSESSMENT_GAP) {
      if (!input.probeGaps().isEmpty()) {
        throw new IllegalArgumentException("非评估追问不能携带 ProbeGap");
      }
      return null;
    }
    if (input.probeGaps().isEmpty()) {
      throw new IllegalArgumentException("评估追问必须携带 ProbeGap");
    }
    return input.probeGaps().getFirst();
  }

  private int followUpDepth(
      Integer parentTurnIndex,
      List<AdaptiveInterviewTurn> history
  ) {
    if (parentTurnIndex == null) {
      return 0;
    }
    Map<Integer, AdaptiveInterviewTurn> turnsByIndex = new HashMap<>();
    history.forEach(turn -> turnsByIndex.put(turn.turnIndex(), turn));
    int depth = 0;
    Integer current = parentTurnIndex;
    while (current != null) {
      AdaptiveInterviewTurn turn = turnsByIndex.get(current);
      if (turn == null) {
        throw new IllegalArgumentException("父轮次不在当前会话历史中");
      }
      depth++;
      current = turn.provenance().parentTurnIndex();
    }
    return depth;
  }

  /** 组装规划 Agent 所需的上下文，并裁剪过长的本次文档。 */
  public PlannerContext planner(PlannerContext input) {
    return new PlannerContext(
        truncate(input.jd()),
        truncate(input.resume()),
        input.mode(),
        input.candidateLevel(),
        input.practiceScope(),
        input.skillCatalog()
    );
  }

  /**
   * 组装带追问缺口的面试官上下文。
   */
  public InterviewerContext interviewer(
      InterviewerContextInput input
  ) {
    List<AdaptiveInterviewTurn> currentDimensionTurns = input.turns().stream()
        .filter(turn -> turn.dimensionOrder() == input.targetDimensionOrder())
        .toList();
    CandidateAnswer currentDimensionAnswer = input.candidateAnswer();
    if (input.candidateAnswer() != null
        && input.turns().get(input.candidateAnswer().turnIndex() - 1).dimensionOrder()
            != input.targetDimensionOrder()) {
      currentDimensionAnswer = null;
    }
    return new InterviewerContext(
        truncate(input.jd()),
        truncate(input.resume()),
        input.turns().size(),
        input.maxTurns(),
        input.targetDimensionOrder(),
        input.targetDimension(),
        input.targetFocus(),
        input.suggestedTools(),
        input.suggestedSkill(),
        currentDimensionTurns,
        currentDimensionAnswer,
        input.workingMemory(),
        input.episodeHistory(),
        null,
        input.candidateAnswer() != null && input.candidateAnswer().codeSubmission() != null
            ? input.candidateAnswer()
            : null,
        input.project()
    );
  }

  /**
   * 组装“工具结果到达后”的面试官上下文，用于生成基于客观结果的追问。
   */
  public InterviewerContext toolResult(
      ToolResultContextInput input
  ) {
    return new InterviewerContext(
        truncate(input.jd()),
        truncate(input.resume()),
        input.event().turnIndex(),
        input.maxTurns(),
        input.targetDimensionOrder(),
        input.targetDimension(),
        input.targetFocus(),
        input.suggestedTools(),
        input.suggestedSkill(),
        input.turns().stream()
            .filter(turn -> turn.dimensionOrder() == input.targetDimensionOrder())
            .toList(),
        null,
        input.workingMemory(),
        input.episodeHistory(),
        input.event(),
        null,
        input.project()
    );
  }

  private String truncate(String document) {
    if (document == null || document.length() <= MAX_DOCUMENT_CHARS) {
      return document;
    }
    return document.substring(0, MAX_DOCUMENT_CHARS)
        + TRUNCATION_MARKER.formatted(document.length());
  }
}
