package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerContext;
import interview.guide.modules.interview.agent.adaptive.core.context.PlannerContext;
import java.util.List;
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
        input.working(),
        List.of(),
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
        input.working(),
        List.of(),
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
