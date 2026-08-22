package interview.guide.modules.interview.agent.runtime;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.model.CandidateAgentModelConfigEntity;
import interview.guide.modules.interview.agent.model.CandidateAgentModelConfigRequest;
import interview.guide.modules.interview.agent.model.CandidateAgentModelConfigResponse;
import interview.guide.modules.interview.agent.repository.CandidateAgentModelConfigRepository;
import interview.guide.modules.llmprovider.service.ApiKeyEncryptionService;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CandidateAgentModelConfigService {

  private static final int API_KEY_VISIBLE_EDGE_LENGTH = 3;
  private static final int API_KEY_MINIMUM_MASK_LENGTH = API_KEY_VISIBLE_EDGE_LENGTH * 2;

  private final CandidateAgentModelConfigRepository repository;
  private final ApiKeyEncryptionService encryptionService;
  private final CandidateAgentChatClientProvider clientProvider;

  @Transactional(readOnly = true)
  public CandidateAgentModelConfigResponse get(UUID candidateId) {
    return repository.findById(candidateId)
        .map(this::toResponse)
        .orElseGet(CandidateAgentModelConfigResponse::unconfigured);
  }

  @Transactional
  public CandidateAgentModelConfigResponse save(
      UUID candidateId,
      CandidateAgentModelConfigRequest request
  ) {
    String baseUrl = request.baseUrl().trim();
    validateBaseUrl(baseUrl);
    CandidateAgentModelConfigEntity config = repository.findById(candidateId)
        .orElseGet(() -> new CandidateAgentModelConfigEntity(candidateId));
    applyApiKey(config, request.apiKey());
    config.setBaseUrl(baseUrl);
    config.setModel(request.model().trim());
    config.setTemperature(request.temperature());
    CandidateAgentModelConfigEntity saved = repository.save(config);
    clientProvider.invalidate(candidateId);
    return toResponse(saved);
  }

  private void applyApiKey(CandidateAgentModelConfigEntity config, String requestedApiKey) {
    String apiKey = requestedApiKey == null ? null : requestedApiKey.trim();
    if (apiKey == null || apiKey.isEmpty()) {
      if (config.getApiKeyCiphertext() == null) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "首次配置必须填写 API Key");
      }
      return;
    }
    ApiKeyEncryptionService.EncryptedValue encrypted = encryptionService.encrypt(apiKey);
    config.setApiKeyNonce(encrypted.nonce());
    config.setApiKeyCiphertext(encrypted.ciphertext());
  }

  private void validateBaseUrl(String baseUrl) {
    try {
      URI uri = URI.create(baseUrl);
      boolean supportedScheme = "http".equalsIgnoreCase(uri.getScheme())
          || "https".equalsIgnoreCase(uri.getScheme());
      if (!supportedScheme || uri.getHost() == null) {
        throw new IllegalArgumentException();
      }
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(
          ErrorCode.BAD_REQUEST,
          "模型服务地址必须是有效的 HTTP 或 HTTPS URL",
          exception
      );
    }
  }

  private CandidateAgentModelConfigResponse toResponse(
      CandidateAgentModelConfigEntity config
  ) {
    String apiKey = encryptionService.decrypt(
        config.getApiKeyNonce(),
        config.getApiKeyCiphertext()
    );
    return new CandidateAgentModelConfigResponse(
        true,
        config.getBaseUrl(),
        maskApiKey(apiKey),
        config.getModel(),
        config.getTemperature()
    );
  }

  private String maskApiKey(String apiKey) {
    if (apiKey.length() <= API_KEY_MINIMUM_MASK_LENGTH) {
      return "***";
    }
    return apiKey.substring(0, API_KEY_VISIBLE_EDGE_LENGTH)
        + "***"
        + apiKey.substring(apiKey.length() - API_KEY_VISIBLE_EDGE_LENGTH);
  }
}
