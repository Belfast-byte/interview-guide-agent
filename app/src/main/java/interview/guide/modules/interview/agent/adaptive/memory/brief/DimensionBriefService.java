package interview.guide.modules.interview.agent.adaptive.memory.brief;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.context.DimensionBrief;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 维度简报服务，生成某维度的小结供后续轮次使用。
 */
@Service
@RequiredArgsConstructor
public class DimensionBriefService {

  private static final int MAX_FINDINGS_LENGTH = 2_000;

  private final DimensionBriefGenerator generator;

  public DimensionBrief summarize(
      String sessionId,
      PlannedDimension dimension,
      List<AdaptiveInterviewTurn> turns,
      CandidateAnswer answer,
      String llmProvider
  ) {
    List<DimensionBriefTurn> dimensionTurns = DimensionBriefTurn.forDimension(
        turns,
        dimension,
        answer
    );
    DimensionBriefProposal proposal = generator.generate(
        new DimensionBriefRequest(
            sessionId,
            dimension.order(),
            dimension.dimension(),
            dimension.focus(),
            dimensionTurns
        ),
        llmProvider
    );
    validate(proposal, dimensionTurns);
    return new DimensionBrief(
        sessionId,
        dimension.order(),
        dimension.dimension(),
        dimension.focus(),
        proposal.keyFindings().trim(),
        proposal.turnIndexes()
    );
  }

  private void validate(
      DimensionBriefProposal proposal,
      List<DimensionBriefTurn> dimensionTurns
  ) {
    if (proposal == null
        || proposal.keyFindings() == null
        || proposal.keyFindings().isBlank()
        || proposal.keyFindings().length() > MAX_FINDINGS_LENGTH
        || proposal.turnIndexes() == null
        || proposal.turnIndexes().isEmpty()) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "维度小结结果不完整");
    }
    Set<Integer> validIndexes = dimensionTurns.stream()
        .map(DimensionBriefTurn::turnIndex)
        .collect(Collectors.toSet());
    Set<Integer> referencedIndexes = new HashSet<>(proposal.turnIndexes());
    if (referencedIndexes.size() != proposal.turnIndexes().size()
        || !validIndexes.containsAll(referencedIndexes)) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "维度小结引用了非法轮次");
    }
  }
}
