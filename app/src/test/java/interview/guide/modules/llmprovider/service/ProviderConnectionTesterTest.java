package interview.guide.modules.llmprovider.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Provider 文本连接测试组件")
class ProviderConnectionTesterTest {

  private final ProviderConnectionTester tester = new ProviderConnectionTester();

  @Test
  @DisplayName("已有版本路径不会重复追加 v1")
  void versionedBaseUrlDoesNotAppendV1() {
    assertThat(tester.connectivityUrls(
        "https://open.bigmodel.cn/api/coding/paas/v4"
    )).containsExactly("https://open.bigmodel.cn/api/coding/paas/v4/chat/completions");
  }

  @Test
  @DisplayName("无版本路径依次测试原路径和 v1 路径")
  void unversionedBaseUrlTestsBothPaths() {
    assertThat(tester.connectivityUrls("https://api.deepseek.com"))
        .containsExactly(
            "https://api.deepseek.com/chat/completions",
            "https://api.deepseek.com/v1/chat/completions"
        );
  }

  @Test
  @DisplayName("连接测试请求只包含最小文本调用参数")
  void requestBodyContainsOnlyChatFields() {
    Map<String, Object> body = tester.requestBody("kimi-latest");

    assertThat(body)
        .containsEntry("model", "kimi-latest")
        .containsEntry("max_tokens", 1)
        .containsKey("messages")
        .doesNotContainKeys("temperature", "embedding_model");
    assertThat((List<?>) body.get("messages")).hasSize(1);
  }
}
