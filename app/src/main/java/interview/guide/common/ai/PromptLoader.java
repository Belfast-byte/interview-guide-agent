package interview.guide.common.ai;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Prompt 资源加载器，统一从 classpath 加载提示词模板与原始文本。
 */
@Component
public class PromptLoader {

  private final ResourceLoader resourceLoader;

  public PromptLoader(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  /**
   * 加载提示词模板（Spring AI {@code {placeholder}} 语法）。
   *
   * @param path 资源路径
   * @return 提示词模板
   */
  public PromptTemplate loadTemplate(String path) {
    return new PromptTemplate(loadText(path));
  }

  /**
   * 加载提示词原始文本。
   *
   * @param path 资源路径
   * @return 文件内容
   */
  public String loadText(String path) {
    try {
      return resourceLoader.getResource(path).getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Prompt 资源加载失败: " + path, e);
    }
  }
}
