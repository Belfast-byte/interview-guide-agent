package interview.guide.modules.llmprovider.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.config.LlmProviderProperties;
import interview.guide.common.exception.BusinessException;
import interview.guide.modules.llmprovider.model.LlmGlobalSettingEntity;
import interview.guide.modules.llmprovider.model.LlmProviderEntity;
import interview.guide.modules.llmprovider.repository.LlmGlobalSettingRepository;
import interview.guide.modules.llmprovider.repository.LlmProviderRepository;
import interview.guide.modules.voiceinterview.config.VoiceInterviewProperties;
import interview.guide.modules.voiceinterview.service.QwenAsrService;
import interview.guide.modules.voiceinterview.service.QwenTtsService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("系统 Provider 服务隔离测试")
class LlmProviderSystemIsolationTest {

  @Mock private LlmProviderProperties properties;
  @Mock private LlmProviderRegistry registry;
  @Mock private LlmProviderRepository providerRepository;
  @Mock private LlmGlobalSettingRepository globalSettingRepository;
  @Mock private ApiKeyEncryptionService encryptionService;
  @Mock private VoiceInterviewProperties voiceProperties;
  @Mock private QwenAsrService asrService;
  @Mock private QwenTtsService ttsService;

  private LlmProviderConfigService service;

  @BeforeEach
  void setUp() {
    service = new LlmProviderConfigService(
        properties,
        registry,
        providerRepository,
        globalSettingRepository,
        encryptionService,
        voiceProperties,
        asrService,
        ttsService
    );
  }

  @Test
  @DisplayName("管理员列表只读取系统 Provider")
  void listReadsOnlySystemProviders() {
    LlmProviderEntity systemProvider = provider("system-provider");
    when(globalSettingRepository.findById(LlmGlobalSettingEntity.SINGLETON_ID))
        .thenReturn(Optional.of(globalSetting("system-provider")));
    when(providerRepository.findByCandidateIdIsNullOrderByIdAsc())
        .thenReturn(List.of(systemProvider));
    when(encryptionService.decrypt("nonce", "ciphertext")).thenReturn("secret-key");

    assertThat(service.listProviders())
        .extracting(provider -> provider.id())
        .containsExactly("system-provider");
    verify(providerRepository).findByCandidateIdIsNullOrderByIdAsc();
  }

  @Test
  @DisplayName("管理员按 ID 查询不会读取候选人 Provider")
  void getDoesNotReadCandidateProvider() {
    when(globalSettingRepository.findById(LlmGlobalSettingEntity.SINGLETON_ID))
        .thenReturn(Optional.of(globalSetting("system-provider")));
    when(providerRepository.findByIdAndCandidateIdIsNull("candidate-provider"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getProvider("candidate-provider"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Provider 'candidate-provider' 不存在");
  }

  private LlmGlobalSettingEntity globalSetting(String providerId) {
    return LlmGlobalSettingEntity.builder()
        .id(LlmGlobalSettingEntity.SINGLETON_ID)
        .defaultChatProviderId(providerId)
        .defaultEmbeddingProviderId(providerId)
        .build();
  }

  private LlmProviderEntity provider(String id) {
    return LlmProviderEntity.builder()
        .id(id)
        .baseUrl("https://example.com/v1")
        .apiKeyCiphertext("ciphertext")
        .apiKeyNonce("nonce")
        .model("chat-model")
        .supportsEmbedding(false)
        .enabled(true)
        .builtin(true)
        .build();
  }
}
