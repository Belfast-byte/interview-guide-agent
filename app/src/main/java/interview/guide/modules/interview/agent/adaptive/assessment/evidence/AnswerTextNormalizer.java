package interview.guide.modules.interview.agent.adaptive.assessment.evidence;

import java.text.Normalizer;

/**
 * 回答原文文本归一化：去首尾空白、全半角统一（NFKC）、连续空白压缩为单个空格。
 * 用于评估证据引用与追问点锚点的子串匹配，容忍模型输出的全半角和空白差异。
 */
public final class AnswerTextNormalizer {

  private AnswerTextNormalizer() {}

  /**
   * 归一化文本。
   *
   * @param text 原始文本
   * @return 归一化后的文本
   */
  public static String normalize(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFKC)
        .replaceAll("\\s+", " ")
        .trim();
  }
}
