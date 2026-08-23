package interview.guide.modules.interview.agent.adaptive.role;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.config.LlmProviderProperties;
import interview.guide.common.config.LlmProviderProperties.ProviderConfig;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayName("自适应 Agent 模型选项 HTTP 协议")
class AdaptiveModelOptionsHttpContractTest {

  private static final String PROVIDER_ID = "probe";
  private static final String REASONING_EFFORT = "low";
  private static final int PLANNER_MAX_TOKENS = 321;
  private static final int INTERVIEWER_MAX_TOKENS = 123;
  private static final byte[] RESPONSE = """
      {
        "id":"chatcmpl-test","object":"chat.completion","created":0,"model":"test",
        "choices":[{"index":0,"message":{"role":"assistant","content":"ok"},
        "finish_reason":"stop"}],
        "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}
      }
      """.getBytes(StandardCharsets.UTF_8);

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final List<String> requestBodies = new CopyOnWriteArrayList<>();
  private HttpServer server;
  private LlmProviderRegistry registry;
  private AdaptiveModelOptionsFactory factory;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::handleRequest);
    server.start();
    registry = buildRegistry(server.getAddress().getPort(), true);
    factory = buildFactory();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  @DisplayName("Provider 显式关闭 Thinking 时真实请求应发送 disabled")
  void shouldSerializeCompatibleRequestFields() {
    invoke(factory.planner());
    invoke(factory.interviewer(List.of()));

    assertThat(requestBodies).hasSize(2);
    assertPlannerRequest(objectMapper.readTree(requestBodies.get(0)));
    assertInterviewerRequest(objectMapper.readTree(requestBodies.get(1)));
  }

  @Test
  @DisplayName("Provider 未关闭 Thinking 时不应发送私有字段")
  void shouldOmitThinkingWhenNotDisabled() {
    registry = buildRegistry(server.getAddress().getPort(), false);

    invoke(factory.planner());

    assertThat(objectMapper.readTree(requestBodies.getFirst()).get("thinking")).isNull();
  }

  private void assertPlannerRequest(JsonNode request) {
    assertThat(request.path("max_tokens").asInt()).isEqualTo(PLANNER_MAX_TOKENS);
    assertThat(request.path("reasoning_effort").asText()).isEqualTo(REASONING_EFFORT);
    assertThat(request.get("parallel_tool_calls")).isNull();
    assertRequestFields(request);
  }

  private void assertInterviewerRequest(JsonNode request) {
    assertThat(request.path("max_tokens").asInt()).isEqualTo(INTERVIEWER_MAX_TOKENS);
    assertThat(request.path("reasoning_effort").asText()).isEqualTo(REASONING_EFFORT);
    assertThat(request.path("parallel_tool_calls").asBoolean()).isFalse();
    assertRequestFields(request);
  }

  private void assertRequestFields(JsonNode request) {
    assertThat(request.get("max_completion_tokens")).isNull();
    assertThat(request.path("thinking").path("type").asText()).isEqualTo("disabled");
  }

  private void invoke(OpenAiChatOptions.Builder options) {
    ChatClient client = registry.getPlainChatClient(PROVIDER_ID)
        .mutate()
        .defaultOptions(options)
        .build();
    client.prompt("hi").call().content();
  }

  private void handleRequest(HttpExchange exchange) throws IOException {
    byte[] request = exchange.getRequestBody().readAllBytes();
    requestBodies.add(new String(request, StandardCharsets.UTF_8));
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, RESPONSE.length);
    try (OutputStream output = exchange.getResponseBody()) {
      output.write(RESPONSE);
    }
  }

  private LlmProviderRegistry buildRegistry(int port, boolean thinkingDisabled) {
    LlmProviderProperties properties = new LlmProviderProperties();
    ProviderConfig config = new ProviderConfig();
    config.setBaseUrl("http://127.0.0.1:" + port);
    config.setApiKey("test-key");
    config.setModel("test-model");
    config.setThinkingDisabled(thinkingDisabled);
    Map<String, ProviderConfig> providers = new HashMap<>();
    providers.put(PROVIDER_ID, config);
    properties.setProviders(providers);
    properties.setDefaultProvider(PROVIDER_ID);
    return new LlmProviderRegistry(
        properties, DefaultToolCallingManager.builder().build(), null, null);
  }

  private AdaptiveModelOptionsFactory buildFactory() {
    AdaptiveAgentProperties properties = new AdaptiveAgentProperties();
    properties.setPlannerMaxOutputTokens(PLANNER_MAX_TOKENS);
    properties.setInterviewerMaxOutputTokens(INTERVIEWER_MAX_TOKENS);
    properties.setReasoningEffort(REASONING_EFFORT);
    return new AdaptiveModelOptionsFactory(properties);
  }
}
