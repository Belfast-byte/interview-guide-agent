package interview.guide.modules.interview.agent.runtime;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.model.CandidateAgentModelConfigEntity;
import interview.guide.modules.interview.agent.model.CandidateAgentModelConfigRequest;
import interview.guide.modules.interview.agent.repository.CandidateAgentModelConfigRepository;
import interview.guide.modules.llmprovider.service.ApiKeyEncryptionService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateAgentModelConfigServiceTest {

  private static final UUID CANDIDATE_ID = UUID.randomUUID();

  @Mock
  private CandidateAgentModelConfigRepository repository;
  @Mock
  private ApiKeyEncryptionService encryptionService;
  @Mock
  private CandidateAgentChatClientProvider clientProvider;

  private CandidateAgentModelConfigService service;

  @BeforeEach
  void setUp() {
    service = new CandidateAgentModelConfigService(
        repository,
        encryptionService,
        clientProvider
    );
  }

  @Test
  @DisplayName("候选人没有配置时返回未配置状态")
  void shouldReturnUnconfiguredState() {
    when(repository.findById(CANDIDATE_ID)).thenReturn(Optional.empty());

    assertThat(service.get(CANDIDATE_ID).configured()).isFalse();
  }

  @Test
  @DisplayName("首次配置必须提供 API Key")
  void shouldRequireApiKeyForFirstConfiguration() {
    when(repository.findById(CANDIDATE_ID)).thenReturn(Optional.empty());
    var request = new CandidateAgentModelConfigRequest(
        "https://api.example.com",
        null,
        "example-model",
        0.2
    );

    assertThatThrownBy(() -> service.save(CANDIDATE_ID, request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("首次配置必须填写 API Key");
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("保存配置时加密密钥并只失效当前候选人的客户端")
  void shouldEncryptApiKeyAndInvalidateCandidateClient() {
    when(repository.findById(CANDIDATE_ID)).thenReturn(Optional.empty());
    when(encryptionService.encrypt("secret-key"))
        .thenReturn(new ApiKeyEncryptionService.EncryptedValue("nonce", "ciphertext"));
    when(encryptionService.decrypt("nonce", "ciphertext")).thenReturn("secret-key");
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var request = new CandidateAgentModelConfigRequest(
        "https://api.example.com/",
        "secret-key",
        "example-model",
        0.3
    );

    var response = service.save(CANDIDATE_ID, request);

    ArgumentCaptor<CandidateAgentModelConfigEntity> captor =
        ArgumentCaptor.forClass(CandidateAgentModelConfigEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getCandidateId()).isEqualTo(CANDIDATE_ID);
    assertThat(captor.getValue().getApiKeyCiphertext()).isEqualTo("ciphertext");
    assertThat(response.maskedApiKey()).isEqualTo("sec***key");
    verify(clientProvider).invalidate(CANDIDATE_ID);
  }

  @Test
  @DisplayName("更新配置时留空 API Key 会保留已有密钥")
  void shouldKeepExistingApiKeyWhenUpdateLeavesItBlank() {
    CandidateAgentModelConfigEntity existing = new CandidateAgentModelConfigEntity(CANDIDATE_ID);
    existing.setApiKeyNonce("old-nonce");
    existing.setApiKeyCiphertext("old-ciphertext");
    when(repository.findById(CANDIDATE_ID)).thenReturn(Optional.of(existing));
    when(repository.save(existing)).thenReturn(existing);
    when(encryptionService.decrypt("old-nonce", "old-ciphertext")).thenReturn("old-secret");

    service.save(CANDIDATE_ID, new CandidateAgentModelConfigRequest(
        "http://localhost:1234",
        " ",
        "local-model",
        0.1
    ));

    assertThat(existing.getApiKeyCiphertext()).isEqualTo("old-ciphertext");
    verify(encryptionService, never()).encrypt(any());
  }
}
