package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.CoveredTopic;
import interview.guide.modules.interview.agent.adaptive.core.DimensionBrief;
import interview.guide.modules.interview.agent.adaptive.core.InterviewerContext;
import interview.guide.modules.interview.agent.adaptive.core.PlannerContext;
import interview.guide.modules.interview.agent.adaptive.core.PlanningSkill;
import interview.guide.modules.interview.agent.adaptive.core.ProjectInterviewContext;
import interview.guide.modules.interview.agent.adaptive.core.UnverifiedClaim;
import interview.guide.modules.interview.agent.adaptive.core.ToolResultEvent;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 上下文装配器，为规划器、面试官、评估器等角色组装所需上下文。
 */
@Component
public class ContextAssembler {

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
    return new PlannerContext(jd, resume, coveredTopics, unverifiedClaims, skillCatalog);
  }

  /**
   * 组装面试官 Agent 的上下文，包含项目代码分析结果。
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
   * @param candidateAnswer 当前候选人回答
   * @param dimensionBriefs 已有维度简报
   * @param project 项目代码分析上下文
   * @return 面试官上下文
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
        jd,
        resume,
        turns.size(),
        maxTurns,
        targetDimensionOrder,
        targetDimension,
        targetFocus,
        suggestedTools,
        suggestedSkill,
        currentDimensionTurns,
        currentDimensionAnswer,
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
   * 组装面试官 Agent 的上下文（无项目代码分析）。
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
   * @param candidateAnswer 当前候选人回答
   * @param dimensionBriefs 已有维度简报
   * @return 面试官上下文
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
      List<DimensionBrief> dimensionBriefs
  ) {
    return interviewer(
        jd,
        resume,
        maxTurns,
        targetDimensionOrder,
        targetDimension,
        targetFocus,
        suggestedTools,
        suggestedSkill,
        turns,
        candidateAnswer,
        dimensionBriefs,
        null
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
        jd,
        resume,
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
        dimensionBriefs.stream()
            .filter(brief -> brief.dimensionOrder() != targetDimensionOrder)
            .toList(),
        event,
        null,
        project
    );
  }
}
