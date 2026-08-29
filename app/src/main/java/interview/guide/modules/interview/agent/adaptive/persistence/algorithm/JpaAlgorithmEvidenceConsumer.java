package interview.guide.modules.interview.agent.adaptive.persistence.algorithm;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmEvidenceConsumer;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionEntity;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionRepository;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionStatus;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxVerdict;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class JpaAlgorithmEvidenceConsumer implements AlgorithmEvidenceConsumer {

  private final SandboxExecutionRepository executionRepository;
  private final AdaptiveAgentTurnRepository turnRepository;
  private final AdaptiveAgentAssessmentRepository assessmentRepository;
  private final AdaptiveAgentEvidenceRepository evidenceRepository;

  @Override
  @Transactional
  public boolean consume(String executionId) {
    SandboxExecutionEntity entity = executionRepository.findLockedById(executionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "判题提交不存在"));
    if (!entity.markConsumed()) {
      return false;
    }
    SandboxExecution execution = entity.toDomain();
    if (!isCandidateEvidence(execution)) {
      return true;
    }
    int turnIndex = turnRepository.findById(execution.turnId())
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "面试轮次不存在"))
        .turnIndex();
    AdaptiveAgentAssessmentEntity assessment = assessmentRepository
        .findBySessionIdAndTurnIndex(execution.sessionId(), turnIndex)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "判题结果缺少对应评估"));
    evidenceRepository.saveAndFlush(new AdaptiveAgentEvidenceEntity(
        assessment,
        execution.sessionId(),
        turnIndex,
        new ValidatedAssessmentEvidence(
            EvidenceType.TOOL_RESULT,
            null,
            null,
            execution.id()
        )
    ));
    return true;
  }

  private boolean isCandidateEvidence(SandboxExecution execution) {
    return execution.supersededBy() == null
        && execution.status() == SandboxExecutionStatus.DONE
        && execution.verdict() != SandboxVerdict.IE;
  }
}
