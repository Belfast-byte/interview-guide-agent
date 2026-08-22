package interview.guide.modules.interview.agent.adaptive.memory.claim;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.context.PlanningSkill;
import interview.guide.modules.interview.agent.adaptive.memory.brief.DimensionBriefTurn;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 候选人声明抽取服务，从回答中提取可验证声明。
 */
@Service
@RequiredArgsConstructor
public class CandidateClaimExtractionService {

  private static final int MAX_CLAIMS_PER_DIMENSION = 20;

  private final CandidateClaimGenerator generator;

  public List<CandidateClaim> extract(
      String sessionId,
      PlannedDimension dimension,
      List<AdaptiveInterviewTurn> turns,
      CandidateAnswer answer,
      List<PlanningSkill> skillCatalog,
      String llmProvider
  ) {
    List<DimensionBriefTurn> dimensionTurns = DimensionBriefTurn.forDimension(
        turns,
        dimension,
        answer
    );
    CandidateClaimsProposal proposal = generator.generate(
        new CandidateClaimExtractionRequest(sessionId, dimensionTurns, skillCatalog),
        llmProvider
    );
    validate(proposal, dimensionTurns, skillCatalog);
    return proposal.claims();
  }

  private void validate(
      CandidateClaimsProposal proposal,
      List<DimensionBriefTurn> turns,
      List<PlanningSkill> skillCatalog
  ) {
    if (proposal == null
        || proposal.claims() == null
        || proposal.claims().size() > MAX_CLAIMS_PER_DIMENSION) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "候选人声明抽取结果不完整");
    }
    if (new HashSet<>(proposal.claims()).size() != proposal.claims().size()) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "候选人声明抽取结果包含重复项");
    }
    Set<Integer> turnIndexes = turns.stream()
        .map(DimensionBriefTurn::turnIndex)
        .collect(Collectors.toSet());
    Map<String, PlanningSkill> skills = skillCatalog.stream()
        .collect(Collectors.toMap(PlanningSkill::skillId, Function.identity()));
    for (CandidateClaim claim : proposal.claims()) {
      if (claim == null
          || claim.type() == null
          || !turnIndexes.contains(claim.sourceTurnIndex())) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "候选人声明引用了非法轮次");
      }
      PlanningSkill skill = skills.get(claim.skillId());
      if (skill == null || !skill.focusIds().contains(claim.focusId())) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "候选人声明包含未知主题标识");
      }
    }
  }
}
