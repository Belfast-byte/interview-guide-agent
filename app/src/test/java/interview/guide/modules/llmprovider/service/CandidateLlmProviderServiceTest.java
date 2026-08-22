package interview.guide.modules.llmprovider.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.llmprovider.dto.CreateCandidateProviderRequest;
import interview.guide.modules.llmprovider.dto.ProviderTestResult;
import interview.guide.modules.llmprovider.dto.UpdateCandidateProviderRequest;
import interview.guide.modules.llmprovider.model.CandidateLlmSettingEntity;
import interview.guide.modules.llmprovider.model.LlmProviderEntity;
import interview.guide.modules.llmprovider.repository.CandidateLlmSettingRepository;
import interview.guide.modules.llmprovider.repository.LlmProviderRepository;
import interview.guide.modules.llmprovider.service.ProviderConnectionTester.ProviderConnectionConfig;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("候选人 Provider 服务测试")
class CandidateLlmProviderServiceTest {

  @Mock private LlmProviderRepository providerRepository;
  @Mock private CandidateLlmSettingRepository settingRepository;
  @Mock private ApiKeyEncryptionService encryptionService;
  @Mock private LlmProviderRegistry registry;
  @Mock private ProviderConnectionTester connectionTester;

  private CandidateLlmProviderService service;
  private UUID candidateId;

  @BeforeEach
  void setUp() {
    service = new CandidateLlmProviderService(
        providerRepository,
        settingRepository,
        encryptionService,
        registry,
        connectionTester
    );
    candidateId = UUID.randomUUID();
  }

  @Test
  @DisplayName("列表只读取当前候选人并返回掩码和默认角色")
  void listReturnsOwnedProvidersWithMaskedKeyAndDefaults() {
    LlmProviderEntity provider = provider("provider-1", true);
    CandidateLlmSettingEntity setting = new CandidateLlmSettingEntity(candidateId);
    setting.setDefaultChatProviderId("provider-1");
    setting.setDefaultEmbeddingProviderId("provider-1");
    when(settingRepository.findById(candidateId)).thenReturn(Optional.of(setting));
    when(providerRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId))
        .thenReturn(List.of(provider));
    when(encryptionService.decrypt("nonce", "ciphertext"))
        .thenReturn("sk-abcdefxyz");

    var responses = service.list(candidateId);

    assertThat(responses).singleElement().satisfies(response -> {
      assertThat(response.id()).isEqualTo("provider-1");
      assertThat(response.maskedApiKey()).isEqualTo("sk-***xyz");
      assertThat(response.defaultChatProvider()).isTrue();
      assertThat(response.defaultEmbeddingProvider()).isTrue();
    });
  }

  @Test
  @DisplayName("创建时由服务端生成 ID 且只保存密文")
  void createGeneratesIdAndStoresCiphertext() {
    when(encryptionService.encrypt("secret-key"))
        .thenReturn(new ApiKeyEncryptionService.EncryptedValue("nonce-new", "cipher-new"));
    when(providerRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.create(candidateId, createRequest());

    ArgumentCaptor<LlmProviderEntity> captor = ArgumentCaptor.forClass(LlmProviderEntity.class);
    verify(providerRepository).saveAndFlush(captor.capture());
    LlmProviderEntity saved = captor.getValue();
    assertThat(saved.getId()).isNotBlank();
    assertThat(saved.getCandidateId()).isEqualTo(candidateId);
    assertThat(saved.getDisplayName()).isEqualTo("我的 Provider");
    assertThat(saved.getApiKeyCiphertext()).isEqualTo("cipher-new");
    assertThat(saved.getApiKeyNonce()).isEqualTo("nonce-new");
    assertThat(saved.getApiKeyCiphertext()).doesNotContain("secret-key");
  }

  @Test
  @DisplayName("同一候选人名称重复时返回明确业务错误")
  void createTranslatesUniqueConstraintFailure() {
    when(encryptionService.encrypt("secret-key"))
        .thenReturn(new ApiKeyEncryptionService.EncryptedValue("nonce-new", "cipher-new"));
    when(providerRepository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThatThrownBy(() -> service.create(candidateId, createRequest()))
        .isInstanceOf(BusinessException.class)
        .hasMessage("当前账号已存在同名 Provider");
  }

  @Test
  @DisplayName("编辑时 API Key 留空保留原密文")
  void updateKeepsApiKeyWhenBlank() {
    LlmProviderEntity provider = provider("provider-1", false);
    when(providerRepository.findByIdAndCandidateId("provider-1", candidateId))
        .thenReturn(Optional.of(provider));
    when(providerRepository.saveAndFlush(provider)).thenReturn(provider);

    service.update(candidateId, "provider-1", updateRequest("   "));

    assertThat(provider.getApiKeyNonce()).isEqualTo("nonce");
    assertThat(provider.getApiKeyCiphertext()).isEqualTo("ciphertext");
    verify(encryptionService, never()).encrypt(any());
    verify(registry).reload();
  }

  @Test
  @DisplayName("测试连接只使用当前候选人的 Provider")
  void testUsesOwnedProviderConfiguration() {
    LlmProviderEntity provider = provider("provider-1", false);
    ProviderTestResult expected = new ProviderTestResult(true, "连接成功", "chat-model");
    when(providerRepository.findByIdAndCandidateId("provider-1", candidateId))
        .thenReturn(Optional.of(provider));
    when(encryptionService.decrypt("nonce", "ciphertext")).thenReturn("secret-key");
    when(connectionTester.test(
        "provider-1",
        new ProviderConnectionConfig("https://example.com/v1", "secret-key", "chat-model")
    )).thenReturn(expected);

    assertThat(service.test(candidateId, "provider-1")).isSameAs(expected);
  }

  @Test
  @DisplayName("其他候选人的 Provider 统一表现为不存在")
  void foreignProviderIsNotDisclosed() {
    when(providerRepository.findByIdAndCandidateId("provider-other", candidateId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.test(candidateId, "provider-other"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Provider 不存在或不可用");
    verify(connectionTester, never()).test(any(), any());
  }

  @Test
  @DisplayName("设置默认文本 Provider 创建候选人设置")
  void setDefaultChatCreatesCandidateSetting() {
    when(providerRepository.findByIdAndCandidateId("provider-1", candidateId))
        .thenReturn(Optional.of(provider("provider-1", false)));
    when(settingRepository.findById(candidateId)).thenReturn(Optional.empty());

    service.setDefaultChat(candidateId, "provider-1");

    ArgumentCaptor<CandidateLlmSettingEntity> captor =
        ArgumentCaptor.forClass(CandidateLlmSettingEntity.class);
    verify(settingRepository).save(captor.capture());
    assertThat(captor.getValue().getCandidateId()).isEqualTo(candidateId);
    assertThat(captor.getValue().getDefaultChatProviderId()).isEqualTo("provider-1");
  }

  @Test
  @DisplayName("没有嵌入模型时不能设为默认嵌入 Provider")
  void setDefaultEmbeddingRequiresEmbeddingModel() {
    when(providerRepository.findByIdAndCandidateId("provider-1", candidateId))
        .thenReturn(Optional.of(provider("provider-1", false)));

    assertThatThrownBy(() -> service.setDefaultEmbedding(candidateId, "provider-1"))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getCode())
        .isEqualTo(ErrorCode.PROVIDER_EMBEDDING_NOT_CONFIGURED.getCode());
    verify(settingRepository, never()).save(any());
  }

  private CreateCandidateProviderRequest createRequest() {
    return new CreateCandidateProviderRequest(
        " 我的 Provider ",
        " https://example.com/v1 ",
        " secret-key ",
        " chat-model ",
        null,
        null,
        0.2
    );
  }

  private UpdateCandidateProviderRequest updateRequest(String apiKey) {
    return new UpdateCandidateProviderRequest(
        "我的 Provider",
        "https://example.com/v1",
        apiKey,
        "chat-model",
        null,
        null,
        0.2
    );
  }

  private LlmProviderEntity provider(String id, boolean supportsEmbedding) {
    return LlmProviderEntity.builder()
        .id(id)
        .candidateId(candidateId)
        .displayName("我的 Provider")
        .baseUrl("https://example.com/v1")
        .apiKeyNonce("nonce")
        .apiKeyCiphertext("ciphertext")
        .model("chat-model")
        .embeddingModel(supportsEmbedding ? "embedding-model" : null)
        .embeddingDimensions(supportsEmbedding ? 1024 : null)
        .supportsEmbedding(supportsEmbedding)
        .enabled(true)
        .builtin(false)
        .build();
  }
}
