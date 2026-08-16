package interview.guide.modules.interview.agent.adaptive.algorithm.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import interview.guide.common.exception.BusinessException;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmInterviewProperties;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxLanguage;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlgorithmSourceStorageTest {

  @Mock
  private FileStorageService fileStorageService;

  @Test
  @DisplayName("源码原文写入对象存储并生成 SHA-256 引用")
  void shouldStoreOriginalSourceAndHash() {
    AlgorithmInterviewProperties properties = new AlgorithmInterviewProperties();
    AlgorithmSourceStorage storage = new AlgorithmSourceStorage(fileStorageService, properties);
    String source = "class Main {}";

    StoredAlgorithmSource stored = storage.store("session-1", SandboxLanguage.JAVA, source);

    ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
    verify(fileStorageService).uploadFile(
        org.mockito.ArgumentMatchers.startsWith("sandbox/sources/session-1/"),
        bytes.capture(),
        eq("text/plain; charset=UTF-8")
    );
    assertThat(new String(bytes.getValue(), StandardCharsets.UTF_8)).isEqualTo(source);
    assertThat(stored.codeRef()).endsWith(".java");
    assertThat(stored.codeHash()).hasSize(64);
  }

  @Test
  @DisplayName("UTF-8 源码超过边界时拒绝且不上传")
  void shouldRejectSourceBeyondByteLimit() {
    AlgorithmInterviewProperties properties = new AlgorithmInterviewProperties();
    properties.setMaxSourceBytes(4);
    AlgorithmSourceStorage storage = new AlgorithmSourceStorage(fileStorageService, properties);

    assertThatThrownBy(() -> storage.store("session-1", SandboxLanguage.PYTHON, "你好"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("64KB");
  }
}
