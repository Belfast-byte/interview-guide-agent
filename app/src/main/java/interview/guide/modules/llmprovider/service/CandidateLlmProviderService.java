package interview.guide.modules.llmprovider.service;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.llmprovider.dto.CandidateProviderResponse;
import interview.guide.modules.llmprovider.dto.CreateCandidateProviderRequest;
import interview.guide.modules.llmprovider.dto.ProviderTestResult;
import interview.guide.modules.llmprovider.dto.UpdateCandidateProviderRequest;
import interview.guide.modules.llmprovider.model.CandidateLlmSettingEntity;
import interview.guide.modules.llmprovider.model.LlmProviderEntity;
import interview.guide.modules.llmprovider.repository.CandidateLlmSettingRepository;
import interview.guide.modules.llmprovider.repository.LlmProviderRepository;
import interview.guide.modules.llmprovider.service.ProviderConnectionTester.ProviderConnectionConfig;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CandidateLlmProviderService {

  private static final Set<AdaptiveSessionStatus> ACTIVE_SESSION_STATUSES = Set.of(
      AdaptiveSessionStatus.CREATED,
      AdaptiveSessionStatus.IN_PROGRESS
  );

  private final LlmProviderRepository providerRepository;
  private final CandidateLlmSettingRepository settingRepository;
  private final ApiKeyEncryptionService encryptionService;
  private final LlmProviderRegistry registry;
  private final ProviderConnectionTester connectionTester;
  private final AdaptiveAgentSessionRepository sessionRepository;

  @Transactional(readOnly = true)
  public List<CandidateProviderResponse> list(UUID candidateId) {
    CandidateLlmSettingEntity setting = settingRepository.findById(candidateId).orElse(null);
    return providerRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId).stream()
        .map(provider -> toResponse(provider, setting))
        .toList();
  }

  @Transactional
  public void create(UUID candidateId, CreateCandidateProviderRequest request) {
    String embeddingModel = trimOrNull(request.embeddingModel());
    validateEmbeddingDimensions(embeddingModel, request.embeddingDimensions());
    ApiKeyEncryptionService.EncryptedValue encrypted = encryptionService.encrypt(
        request.apiKey().trim()
    );
    LlmProviderEntity provider = LlmProviderEntity.builder()
        .id(UUID.randomUUID().toString())
        .candidateId(candidateId)
        .displayName(request.displayName().trim())
        .baseUrl(request.baseUrl().trim())
        .apiKeyCiphertext(encrypted.ciphertext())
        .apiKeyNonce(encrypted.nonce())
        .model(request.model().trim())
        .embeddingModel(embeddingModel)
        .embeddingDimensions(embeddingModel == null ? null : request.embeddingDimensions())
        .supportsEmbedding(embeddingModel != null)
        .temperature(request.temperature())
        .thinkingDisabled(request.thinkingDisabled())
        .enabled(true)
        .builtin(false)
        .build();
    save(provider);
  }

  @Transactional
  public void update(
      UUID candidateId,
      String providerId,
      UpdateCandidateProviderRequest request
  ) {
    LlmProviderEntity provider = getOwned(candidateId, providerId);
    requireNotInUse(providerId);
    String embeddingModel = trimOrNull(request.embeddingModel());
    validateEmbeddingDimensions(embeddingModel, request.embeddingDimensions());
    provider.setDisplayName(request.displayName().trim());
    provider.setBaseUrl(request.baseUrl().trim());
    provider.setModel(request.model().trim());
    provider.setEmbeddingModel(embeddingModel);
    provider.setEmbeddingDimensions(embeddingModel == null ? null : request.embeddingDimensions());
    provider.setSupportsEmbedding(embeddingModel != null);
    provider.setTemperature(request.temperature());
    provider.setThinkingDisabled(request.thinkingDisabled());
    updateApiKey(provider, request.apiKey());
    save(provider);
    registry.reload();
  }

  public ProviderTestResult test(UUID candidateId, String providerId) {
    LlmProviderEntity provider = getOwned(candidateId, providerId);
    return connectionTester.test(
        providerId,
        new ProviderConnectionConfig(
            provider.getBaseUrl(),
            decryptApiKey(provider),
            provider.getModel()
        )
    );
  }

  @Transactional(readOnly = true)
  public CandidateChatProvider resolveChatProvider(
      UUID candidateId,
      String requestedProviderId
  ) {
    CandidateLlmSettingEntity setting = settingRepository.findById(candidateId)
        .filter(candidateSetting -> candidateSetting.getDefaultChatProviderId() != null)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.PROVIDER_DEFAULT_REQUIRED,
            "请先设置默认文本 Provider"
        ));
    String selectedId = trimOrNull(requestedProviderId);
    LlmProviderEntity provider = getOwned(
        candidateId,
        selectedId == null ? setting.getDefaultChatProviderId() : selectedId
    );
    if (!provider.isEnabled()) {
      throw new BusinessException(ErrorCode.PROVIDER_NOT_FOUND, "Provider 不存在或不可用");
    }
    return new CandidateChatProvider(
        provider.getId(),
        provider.getDisplayName(),
        provider.getModel()
    );
  }

  @Transactional
  public void setDefaultChat(UUID candidateId, String providerId) {
    getOwned(candidateId, providerId);
    CandidateLlmSettingEntity setting = getOrCreateSetting(candidateId);
    setting.setDefaultChatProviderId(providerId);
    settingRepository.save(setting);
  }

  @Transactional
  public void setDefaultEmbedding(UUID candidateId, String providerId) {
    LlmProviderEntity provider = getOwned(candidateId, providerId);
    if (!provider.isSupportsEmbedding() || provider.getEmbeddingModel() == null) {
      throw new BusinessException(
          ErrorCode.PROVIDER_EMBEDDING_NOT_CONFIGURED,
          "Provider 未配置嵌入模型，不能设为默认嵌入 Provider"
      );
    }
    CandidateLlmSettingEntity setting = getOrCreateSetting(candidateId);
    setting.setDefaultEmbeddingProviderId(providerId);
    settingRepository.save(setting);
  }

  @Transactional
  public void delete(UUID candidateId, String providerId) {
    LlmProviderEntity provider = getOwned(candidateId, providerId);
    requireNotDefault(candidateId, providerId);
    requireNotInUse(providerId);
    providerRepository.delete(provider);
    registry.reload();
  }

  LlmProviderEntity getOwned(UUID candidateId, String providerId) {
    return providerRepository.findByIdAndCandidateId(providerId, candidateId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.PROVIDER_NOT_FOUND,
            "Provider 不存在或不可用"
        ));
  }

  private CandidateProviderResponse toResponse(
      LlmProviderEntity provider,
      CandidateLlmSettingEntity setting
  ) {
    String defaultChatId = setting == null ? null : setting.getDefaultChatProviderId();
    String defaultEmbeddingId = setting == null
        ? null
        : setting.getDefaultEmbeddingProviderId();
    return new CandidateProviderResponse(
        provider.getId(),
        provider.getDisplayName(),
        provider.getBaseUrl(),
        maskApiKey(decryptApiKey(provider)),
        provider.getModel(),
        provider.getEmbeddingModel(),
        provider.getEmbeddingDimensions(),
        provider.isSupportsEmbedding(),
        provider.getTemperature(),
        provider.isThinkingDisabled(),
        provider.getId().equals(defaultChatId),
        provider.getId().equals(defaultEmbeddingId)
    );
  }

  private CandidateLlmSettingEntity getOrCreateSetting(UUID candidateId) {
    return settingRepository.findById(candidateId)
        .orElseGet(() -> new CandidateLlmSettingEntity(candidateId));
  }

  private void requireNotDefault(UUID candidateId, String providerId) {
    settingRepository.findById(candidateId).ifPresent(setting -> {
      if (providerId.equals(setting.getDefaultChatProviderId())
          || providerId.equals(setting.getDefaultEmbeddingProviderId())) {
        throw new BusinessException(
            ErrorCode.PROVIDER_DEFAULT_CANNOT_DELETE,
            "默认 Provider 不可删除，请先切换默认角色"
        );
      }
    });
  }

  private void requireNotInUse(String providerId) {
    if (sessionRepository.existsByLlmProviderAndTenantIdIsNullAndStatusIn(
        providerId,
        ACTIVE_SESSION_STATUSES
    )) {
      throw new BusinessException(
          ErrorCode.PROVIDER_IN_USE,
          "Provider 正被创建中或进行中的面试使用"
      );
    }
  }

  private void updateApiKey(LlmProviderEntity provider, String apiKey) {
    String normalizedApiKey = trimOrNull(apiKey);
    if (normalizedApiKey == null) {
      return;
    }
    ApiKeyEncryptionService.EncryptedValue encrypted = encryptionService.encrypt(normalizedApiKey);
    provider.setApiKeyCiphertext(encrypted.ciphertext());
    provider.setApiKeyNonce(encrypted.nonce());
  }

  private void validateEmbeddingDimensions(String embeddingModel, Integer dimensions) {
    if (embeddingModel == null && dimensions != null) {
      throw new BusinessException(
          ErrorCode.BAD_REQUEST,
          "未配置嵌入模型时不能填写嵌入维度"
      );
    }
  }

  private void save(LlmProviderEntity provider) {
    try {
      providerRepository.saveAndFlush(provider);
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(
          ErrorCode.PROVIDER_ALREADY_EXISTS,
          "当前账号已存在同名 Provider",
          exception
      );
    }
  }

  private String decryptApiKey(LlmProviderEntity provider) {
    return encryptionService.decrypt(
        provider.getApiKeyNonce(),
        provider.getApiKeyCiphertext()
    );
  }

  private String maskApiKey(String apiKey) {
    if (apiKey.length() <= 6) {
      return "***";
    }
    return apiKey.substring(0, 3) + "***" + apiKey.substring(apiKey.length() - 3);
  }

  private String trimOrNull(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
