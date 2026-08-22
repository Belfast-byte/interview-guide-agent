package interview.guide.modules.interview.agent.adaptive.mcp;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataRetrievalFailureException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptiveMcpAuditServiceFailureTest {

  @Test
  @DisplayName("审计写入失败向调用方传播")
  void shouldPropagateAuditWriteFailure() {
    AdaptiveMcpAuditRepository repository = mock(AdaptiveMcpAuditRepository.class);
    when(repository.save(any())).thenThrow(new DataRetrievalFailureException("DB 抖动"));
    AdaptiveMcpAuditService auditService = new AdaptiveMcpAuditService(repository);

    assertThatThrownBy(() -> auditService.record(
        new McpTenantPrincipal(
            "tenant-a",
            "credential-a",
            Set.of(McpInterviewScope.INTERVIEW_READ)
        ),
        "interview.get_status",
        "session-1",
        McpAuditOutcome.SUCCEEDED
    )).isInstanceOf(DataRetrievalFailureException.class)
        .hasMessage("DB 抖动");
  }
}
