package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.common.security.AuthenticatedUser;
import interview.guide.modules.auth.domain.UserRole;
import interview.guide.modules.interview.agent.adaptive.application.CandidateMemoryQueryService;
import interview.guide.modules.interview.agent.adaptive.application.CandidateMemoryQueryService.MemoryPage;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CandidateMemoryControllerTest {
  @Test
  void queriesAuthenticatedCandidateAndReturnsExplicitPagination() {
    var queries = mock(CandidateMemoryQueryService.class);
    var candidate = UUID.fromString("11111111-1111-1111-1111-111111111111");
    var owner = new MemoryOwner(null, candidate.toString());
    when(queries.read(owner, 0)).thenReturn(new MemoryPage(candidate.toString(), List.of(), Page.empty()));

    var result = new CandidateMemoryController(queries)
        .get(new AuthenticatedUser(candidate, UserRole.CANDIDATE), 0).getData();

    assertThat(result.candidateId()).isEqualTo(candidate.toString());
    assertThat(result.skills()).isEmpty();
    assertThat(result.episodes().content()).isEmpty();
    assertThat(result.episodes().totalElements()).isZero();
    verify(queries).read(owner, 0);
  }
}
