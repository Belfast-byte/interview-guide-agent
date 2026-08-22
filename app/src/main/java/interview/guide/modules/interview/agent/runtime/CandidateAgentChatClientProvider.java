package interview.guide.modules.interview.agent.runtime;

import com.openai.client.OpenAIClient;
import interview.guide.common.ai.ApiPathResolver;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.model.CandidateAgentModelConfigEntity;
import interview.guide.modules.interview.agent.repository.CandidateAgentModelConfigRepository;
import interview.guide.modules.llmprovider.service.ApiKeyEncryptionService;
import io.micrometer.observation.ObservationRegistry;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class CandidateAgentChatClientProvider {

  private static final double DEFAULT_TEMPERATURE = 0.2;

  private final CandidateAgentModelConfigRepository repository;
  private final ApiKeyEncryptionService encryptionService;
  private final ObservationRegistry observationRegistry;
  private final Map<UUID, CachedClient> cache = new ConcurrentHashMap<>();

  public CandidateAgentChatClientProvider(
      CandidateAgentModelConfigRepository repository,
      ApiKeyEncryptionService encryptionService,
      ObjectProvider<ObservationRegistry> observationRegistry
  ) {
    this.repository = repository;
    this.encryptionService = encryptionService;
    this.observationRegistry = observationRegistry.getIfAvailable(
        () -> ObservationRegistry.NOOP);
  }

  public ChatClient get(UUID candidateId) {
    CandidateAgentModelConfigEntity config = repository.findById(candidateId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.PROVIDER_NOT_FOUND,
            "请先配置 Agent 面试模型"
        ));
    CachedClient cached = cache.get(candidateId);
    if (cached != null && cached.updatedAt().equals(config.getUpdatedAt())) {
      return cached.client();
    }
    ChatClient client = createClient(config);
    cache.put(candidateId, new CachedClient(config.getUpdatedAt(), client));
    return client;
  }

  public void invalidate(UUID candidateId) {
    cache.remove(candidateId);
  }

  private ChatClient createClient(CandidateAgentModelConfigEntity config) {
    String apiKey = encryptionService.decrypt(
        config.getApiKeyNonce(),
        config.getApiKeyCiphertext()
    );
    OpenAIClient openAiClient = ApiPathResolver.buildOpenAiClient(
        config.getBaseUrl(),
        apiKey
    );
    OpenAiChatOptions options = OpenAiChatOptions.builder()
        .model(config.getModel())
        .temperature(config.getTemperature() != null
            ? config.getTemperature()
            : DEFAULT_TEMPERATURE)
        .build();
    OpenAiChatModel model = OpenAiChatModel.builder()
        .openAiClient(openAiClient)
        .openAiClientAsync(openAiClient.async())
        .options(options)
        .observationRegistry(observationRegistry)
        .build();
    return ChatClient.builder(model).build();
  }

  private record CachedClient(LocalDateTime updatedAt, ChatClient client) {}
}
