package interview.guide.modules.interview.agent.adaptive.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewSummaryProjection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("自适应面试历史查询服务测试")
class AdaptiveInterviewHistoryServiceTest {

  @Mock
  private AdaptiveAgentSessionRepository sessionRepository;

  @Mock
  private AdaptiveInterviewSummaryProjection projection;

  @Test
  @DisplayName("固定按 20 条分页并将 JD 摘要截断为 120 个字符")
  void listUsesFixedPageSizeAndSummarizesJd() {
    UUID candidateId = UUID.randomUUID();
    String longJd = "职位描述\n" + "A".repeat(130);
    PageRequest pageable = PageRequest.of(2, AdaptiveInterviewHistoryService.PAGE_SIZE);
    when(projection.getSessionId()).thenReturn("session-1");
    when(projection.getStatus()).thenReturn(AdaptiveSessionStatus.COMPLETED);
    when(projection.getCurrentTurn()).thenReturn(4);
    when(projection.getMaxTurns()).thenReturn(4);
    when(projection.getJd()).thenReturn(longJd);
    when(projection.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 8, 22, 10, 0));
    when(sessionRepository.findCandidateHistory(candidateId.toString(), pageable))
        .thenReturn(new PageImpl<>(List.of(projection), pageable, 41));
    AdaptiveInterviewHistoryService service = new AdaptiveInterviewHistoryService(
        sessionRepository
    );

    var result = service.list(candidateId, 2);

    assertThat(result.getTotalElements()).isEqualTo(41);
    assertThat(result.getContent()).singleElement().satisfies(summary -> {
      assertThat(summary.sessionId()).isEqualTo("session-1");
      assertThat(summary.jdSummary()).hasSize(120).doesNotContain("\n");
    });
    verify(sessionRepository).findCandidateHistory(candidateId.toString(), pageable);
  }
}
