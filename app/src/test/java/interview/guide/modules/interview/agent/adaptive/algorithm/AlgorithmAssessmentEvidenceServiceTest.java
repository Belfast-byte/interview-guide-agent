package interview.guide.modules.interview.agent.adaptive.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlgorithmAssessmentEvidenceServiceTest {

  @Mock
  private AlgorithmEvidenceSource evidenceSource;

  @Mock
  private AlgorithmAssessmentEvidenceStore evidenceStore;

  @Test
  @DisplayName("评估与判题无论谁先到，已有的有效执行都会挂入同轮证据")
  void shouldAttachAvailableExecutionEvidence() {
    when(evidenceSource.findCandidateEvidenceIds("session-1", 1))
        .thenReturn(Map.of("execution-1", "execution-1"));
    when(evidenceStore.attach("session-1", 1, "execution-1")).thenReturn(true);
    AlgorithmAssessmentEvidenceService service = new AlgorithmAssessmentEvidenceService(
        evidenceSource,
        evidenceStore
    );

    assertThat(service.attachAvailable("session-1", 1)).isEqualTo(1);
    verify(evidenceStore).attach("session-1", 1, "execution-1");
  }

  @Test
  @DisplayName("历史回填扫描会话中存在有效判题结果的轮次")
  void shouldAttachAllCandidateTurnsInSession() {
    when(evidenceSource.findCandidateTurnIndexes("session-old"))
        .thenReturn(Set.of(2, 4));
    when(evidenceSource.findCandidateEvidenceIds("session-old", 2))
        .thenReturn(Map.of("execution-2", "execution-2"));
    when(evidenceSource.findCandidateEvidenceIds("session-old", 4))
        .thenReturn(Map.of("execution-4", "execution-4"));
    when(evidenceStore.attach("session-old", 2, "execution-2")).thenReturn(true);
    when(evidenceStore.attach("session-old", 4, "execution-4")).thenReturn(true);
    AlgorithmAssessmentEvidenceService service = new AlgorithmAssessmentEvidenceService(
        evidenceSource,
        evidenceStore
    );

    assertThat(service.attachAvailable("session-old")).isEqualTo(2);
  }
}
