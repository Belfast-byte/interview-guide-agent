package interview.guide.modules.llmprovider.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.common.security.AuthenticatedUser;
import interview.guide.modules.auth.domain.UserRole;
import interview.guide.modules.llmprovider.dto.CandidateProviderResponse;
import interview.guide.modules.llmprovider.service.CandidateLlmProviderService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("候选人 Provider Controller 测试")
class CandidateLlmProviderControllerTest {

  @Mock private CandidateLlmProviderService providerService;
  @InjectMocks private CandidateLlmProviderController controller;

  @Test
  @DisplayName("列表使用认证主体中的 candidateId")
  void listUsesAuthenticatedCandidateId() {
    UUID candidateId = UUID.randomUUID();
    AuthenticatedUser principal = new AuthenticatedUser(candidateId, UserRole.CANDIDATE);
    CandidateProviderResponse provider = new CandidateProviderResponse(
        "provider-1",
        "我的 Provider",
        "https://example.com/v1",
        "sk-***xyz",
        "chat-model",
        null,
        null,
        false,
        0.2,
        true,
        true,
        false
    );
    when(providerService.list(candidateId)).thenReturn(List.of(provider));

    var result = controller.list(principal);

    assertThat(result.getData()).containsExactly(provider);
    verify(providerService).list(candidateId);
  }
}
