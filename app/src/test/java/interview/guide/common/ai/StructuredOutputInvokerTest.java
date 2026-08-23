package interview.guide.common.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import interview.guide.common.config.LlmProviderProperties;
import interview.guide.common.config.LlmProviderProperties.ProviderConfig;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.DefaultToolCallingManager;

@DisplayName("结构化输出调用器")
class StructuredOutputInvokerTest {

  private static final String PROVIDER_ID = "structured-test";
  private static final Logger LOG = LoggerFactory.getLogger(StructuredOutputInvokerTest.class);
  private static final byte[] INVALID_RESPONSE = response("not-json");
  private static final byte[] VALID_RESPONSE = response("{\"value\":\"ok\"}");

  private final AtomicInteger requestCount = new AtomicInteger();
  private HttpServer server;
  private ChatClient chatClient;
  private StructuredOutputInvoker invoker;
  private boolean returnValidOnSecondRequest;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::handleRequest);
    server.start();
    chatClient = buildRegistry(server.getAddress().getPort()).getPlainChatClient(PROVIDER_ID);
    StructuredOutputProperties properties = new StructuredOutputProperties();
    properties.setStructuredMaxAttempts(2);
    invoker = new StructuredOutputInvoker(properties, null);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  @DisplayName("首次解析失败后只重试一次并返回第二次结果")
  void retriesOnceAfterInvalidJson() {
    returnValidOnSecondRequest = true;

    TestOutput result = invoke();

    assertThat(result.value()).isEqualTo("ok");
    assertThat(requestCount).hasValue(2);
  }

  @Test
  @DisplayName("两次解析都失败时最多发送两次模型请求")
  void stopsAfterOneRetry() {
    assertThatThrownBy(this::invoke)
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("解析失败");

    assertThat(requestCount).hasValue(2);
  }

  private TestOutput invoke() {
    BeanOutputConverter<TestOutput> converter = new BeanOutputConverter<>(TestOutput.class);
    return invoker.invoke(
        chatClient,
        "返回测试对象。\n" + converter.getFormat(),
        "开始",
        converter,
        ErrorCode.AI_SERVICE_ERROR,
        "解析失败: ",
        "structured_output_test",
        LOG
    );
  }

  private void handleRequest(HttpExchange exchange) throws IOException {
    exchange.getRequestBody().readAllBytes();
    int currentRequest = requestCount.incrementAndGet();
    byte[] response = returnValidOnSecondRequest && currentRequest == 2
        ? VALID_RESPONSE
        : INVALID_RESPONSE;
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, response.length);
    try (OutputStream output = exchange.getResponseBody()) {
      output.write(response);
    }
  }

  private LlmProviderRegistry buildRegistry(int port) {
    LlmProviderProperties properties = new LlmProviderProperties();
    ProviderConfig config = new ProviderConfig();
    config.setBaseUrl("http://127.0.0.1:" + port);
    config.setApiKey("test-key");
    config.setModel("test-model");
    properties.setProviders(Map.of(PROVIDER_ID, config));
    properties.setDefaultProvider(PROVIDER_ID);
    return new LlmProviderRegistry(
        properties,
        DefaultToolCallingManager.builder().build(),
        null,
        null
    );
  }

  private static byte[] response(String content) {
    String json = """
        {
          "id":"chatcmpl-test","object":"chat.completion","created":0,"model":"test",
          "choices":[{"index":0,"message":{"role":"assistant","content":%s},
          "finish_reason":"stop"}],
          "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}
        }
        """.formatted(toJsonString(content));
    return json.getBytes(StandardCharsets.UTF_8);
  }

  private static String toJsonString(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  record TestOutput(String value) {}
}
