package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.context.CoveredTopic;
import interview.guide.modules.interview.agent.adaptive.core.context.DimensionBrief;
import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerContext;
import interview.guide.modules.interview.agent.adaptive.core.context.PlannerContext;
import interview.guide.modules.interview.agent.adaptive.core.context.PlanningSkill;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.ProjectInterviewContext;
import interview.guide.modules.interview.agent.adaptive.core.context.UnverifiedClaim;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import interview.guide.modules.interview.agent.adaptive.memory.working.WorkingMemoryInput;
import interview.guide.modules.interview.agent.adaptive.memory.working.WorkingMemorySnapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  /**
   * 组装规划 Agent 所需的上下文。
   *
   * @param jd 职位描述
   * @param resume 候选人简历
   * @param coveredTopics 已覆盖主题（避免重复出题）
   * @param unverifiedClaims 待验证声明
   * @param skillCatalog 可用技能目录
   * @return 规划上下文
   */
  public PlannerContext planner(
      String jd,
      String resume,
      List<CoveredTopic> coveredTopics,
      List<UnverifiedClaim> unverifiedClaims,
      List<PlanningSkill> skillCatalog
  ) {
    return new PlannerContext(
        truncate(jd),
        truncate(resume),
        coveredTopics,
        unverifiedClaims,
        skillCatalog
    );
  }

  /**
   * 组装带追问缺口的面试官上下文。
   */
  public InterviewerContext interviewer(
      String jd,
      String resume,
      int maxTurns,
      int targetDimensionOrder,
      String targetDimension,
      String targetFocus,
      List<String> suggestedTools,
      String suggestedSkill,
      List<AdaptiveInterviewTurn> turns,
      CandidateAnswer candidateAnswer,
      List<ProbeGap> currentAnswerGaps,
      List<DimensionBrief> dimensionBriefs,
      ProjectInterviewContext project
  ) {
    List<AdaptiveInterviewTurn> currentDimensionTurns = turns.stream()
        .filter(turn -> turn.dimensionOrder() == targetDimensionOrder)
        .toList();
    CandidateAnswer currentDimensionAnswer = candidateAnswer;
    if (candidateAnswer != null
        && turns.get(candidateAnswer.turnIndex() - 1).dimensionOrder() != targetDimensionOrder) {
      currentDimensionAnswer = null;
    }
    return new InterviewerContext(
        truncate(jd),
        truncate(resume),
        turns.size(),
        maxTurns,
        targetDimensionOrder,
        targetDimension,
        targetFocus,
        suggestedTools,
        suggestedSkill,
        currentDimensionTurns,
        currentDimensionAnswer,
        currentDimensionAnswer == null ? List.of() : currentAnswerGaps,
        dimensionBriefs.stream()
            .filter(brief -> brief.dimensionOrder() != targetDimensionOrder)
            .toList(),
        null,
        candidateAnswer != null && candidateAnswer.codeSubmission() != null
            ? candidateAnswer
            : null,
        project
    );
  }

  /**
   * 组装“工具结果到达后”的面试官上下文，用于生成基于客观结果的追问。
   *
   * @param jd 职位描述
   * @param resume 候选人简历
   * @param maxTurns 最大轮次数
   * @param targetDimensionOrder 目标维度序号
   * @param targetDimension 目标维度
   * @param targetFocus 当前考察重点
   * @param suggestedTools 建议工具
   * @param suggestedSkill 建议技能
   * @param turns 历史轮次
   * @param event 工具结果事件
   * @param dimensionBriefs 已有维度简报
   * @param project 项目代码分析上下文
   * @return 面试官上下文
   */
  public InterviewerContext toolResult(
      String jd,
      String resume,
      int maxTurns,
      int targetDimensionOrder,
      String targetDimension,
      String targetFocus,
      List<String> suggestedTools,
      String suggestedSkill,
      List<AdaptiveInterviewTurn> turns,
      ToolResultEvent event,
      List<DimensionBrief> dimensionBriefs,
      ProjectInterviewContext project
  ) {
    return new InterviewerContext(
        truncate(jd),
        truncate(resume),
        event.turnIndex(),
        maxTurns,
        targetDimensionOrder,
        targetDimension,
        targetFocus,
        suggestedTools,
        suggestedSkill,
        turns.stream()
            .filter(turn -> turn.dimensionOrder() == targetDimensionOrder)
            .toList(),
        null,
        List.of(),
        dimensionBriefs.stream()
            .filter(brief -> brief.dimensionOrder() != targetDimensionOrder)
            .toList(),
        event,
        null,
        project
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
