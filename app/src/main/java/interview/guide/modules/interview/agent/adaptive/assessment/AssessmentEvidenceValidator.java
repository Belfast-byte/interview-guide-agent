package interview.guide.modules.interview.agent.adaptive.assessment;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssessmentEvidenceValidator {

  private final AssessmentEvidenceSource source;

  public List<ValidatedAssessmentEvidence> validate(
      String sessionId,
      int turnIndex,
      String answer,
      List<AssessmentEvidenceCandidate> candidates
  ) {
    if (candidates.stream()
        .filter(candidate -> candidate.type() == EvidenceType.TOOL_RESULT)
        .anyMatch(candidate -> candidate.toolResultId() == null
            || candidate.toolResultId().isBlank())) {
      throw invalidEvidence();
    }
    Set<String> toolResultIds = candidates.stream()
        .filter(candidate -> candidate.type() == EvidenceType.TOOL_RESULT)
        .map(AssessmentEvidenceCandidate::toolResultId)
        .collect(java.util.stream.Collectors.toSet());
    AssessmentEvidenceFacts facts = toolResultIds.isEmpty()
        ? new AssessmentEvidenceFacts(Map.of())
        : source.load(sessionId, turnIndex, toolResultIds);
    Set<AssessmentEvidenceCandidate> distinct = new HashSet<>(candidates);
    if (distinct.size() != candidates.size()) {
      throw invalidEvidence();
    }
    return candidates.stream()
        .map(candidate -> validate(candidate, answer, facts))
        .toList();
  }

  private ValidatedAssessmentEvidence validate(
      AssessmentEvidenceCandidate candidate,
      String answer,
      AssessmentEvidenceFacts facts
  ) {
    return switch (candidate.type()) {
      case QUOTE -> validateQuote(candidate.quote(), answer);
      case TOOL_RESULT -> validateToolResult(
          candidate.toolResultId(),
          facts
      );
      case CODE_FACT -> throw invalidEvidence();
    };
  }

  private ValidatedAssessmentEvidence validateQuote(String quote, String answer) {
    if (quote == null || quote.isBlank() || !answer.contains(quote)) {
      throw invalidEvidence();
    }
    return new ValidatedAssessmentEvidence(EvidenceType.QUOTE, quote, null);
  }

  private ValidatedAssessmentEvidence validateToolResult(
      String resultId,
      AssessmentEvidenceFacts facts
  ) {
    Long toolCallId = facts.toolCallIdsByResultId().get(resultId);
    String sandboxExecutionId = facts.sandboxExecutionIdsByResultId().get(resultId);
    if (toolCallId == null && sandboxExecutionId == null) {
      throw invalidEvidence();
    }
    return new ValidatedAssessmentEvidence(
        EvidenceType.TOOL_RESULT,
        null,
        toolCallId,
        sandboxExecutionId
    );
  }

  private BusinessException invalidEvidence() {
    return new BusinessException(ErrorCode.AI_SERVICE_ERROR, "评估证据无法追溯");
  }
}
