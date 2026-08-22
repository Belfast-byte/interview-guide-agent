package interview.guide.modules.llmprovider.service;

import interview.guide.common.ai.ApiPathResolver;
import interview.guide.modules.llmprovider.dto.ProviderTestResult;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.InetAddressFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class ProviderConnectionTester {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
  private static final int RESPONSE_PREVIEW_LENGTH = 200;

  public ProviderTestResult test(String providerId, ProviderConnectionConfig config) {
    RestClient restClient = createClient(config.apiKey());
    Map<String, Object> requestBody = requestBody(config.model());
    String lastFailure = "Unknown error";
    for (String targetUrl : connectivityUrls(config.baseUrl())) {
      try {
        invoke(restClient, targetUrl, requestBody);
        log.info("Provider connectivity test succeeded: providerId={}, targetUrl={}",
            providerId, targetUrl);
        return result(true, "连接成功", config.model());
      } catch (RestClientResponseException exception) {
        lastFailure = responseFailure(targetUrl, exception);
        log.warn("Provider connectivity test response failed: providerId={}, targetUrl={}",
            providerId, targetUrl, exception);
      } catch (Exception exception) {
        lastFailure = requestFailure(targetUrl, exception);
        log.warn("Provider connectivity test failed: providerId={}, targetUrl={}",
            providerId, targetUrl, exception);
      }
    }
    return result(false, "连接失败: " + lastFailure, config.model());
  }

  List<String> connectivityUrls(String baseUrl) {
    String normalizedBaseUrl = ApiPathResolver.stripTrailingSlashes(baseUrl);
    LinkedHashSet<String> urls = new LinkedHashSet<>();
    urls.add(normalizedBaseUrl + "/chat/completions");
    if (!ApiPathResolver.baseUrlContainsVersion(normalizedBaseUrl)) {
      urls.add(normalizedBaseUrl + "/v1/chat/completions");
    }
    return List.copyOf(urls);
  }

  Map<String, Object> requestBody(String model) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("messages", List.of(Map.of("role", "user", "content", "Reply with OK only.")));
    body.put("max_tokens", 1);
    return body;
  }

  private RestClient createClient(String apiKey) {
    HttpClientSettings settings = HttpClientSettings.defaults()
        .withConnectTimeout(CONNECT_TIMEOUT)
        .withReadTimeout(READ_TIMEOUT)
        .withInetAddressFilter(allowedAddresses());
    return RestClient.builder()
        .defaultHeader("Authorization", "Bearer " + apiKey)
        .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
        .build();
  }

  private InetAddressFilter allowedAddresses() {
    return InetAddressFilter.externalAddresses()
        .or(InetAddressFilter.adapt(InetAddress::isLoopbackAddress))
        .or("198.18.0.0/15");
  }

  private void invoke(
      RestClient restClient,
      String targetUrl,
      Map<String, Object> requestBody
  ) {
    restClient.post()
        .uri(URI.create(targetUrl))
        .body(requestBody)
        .retrieve()
        .toEntity(String.class);
  }

  private String responseFailure(String targetUrl, RestClientResponseException exception) {
    return "HTTP %s on %s, body=%s".formatted(
        exception.getStatusCode().value(),
        targetUrl,
        abbreviate(exception.getResponseBodyAsString())
    );
  }

  private String requestFailure(String targetUrl, Exception exception) {
    return "%s on %s: %s".formatted(
        exception.getClass().getSimpleName(),
        targetUrl,
        exception.getMessage()
    );
  }

  private String abbreviate(String text) {
    if (text == null || text.isBlank()) {
      return "[no body]";
    }
    String normalized = text.replaceAll("\\s+", " ").trim();
    if (normalized.length() <= RESPONSE_PREVIEW_LENGTH) {
      return normalized;
    }
    return normalized.substring(0, RESPONSE_PREVIEW_LENGTH) + "...";
  }

  private ProviderTestResult result(boolean success, String message, String model) {
    return new ProviderTestResult(success, message, model);
  }

  public record ProviderConnectionConfig(
      String baseUrl,
      String apiKey,
      String model
  ) {}
}
