package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;

public record CodeAnchor(String file, int line) {

  public CodeAnchor {
    if (file == null || file.isBlank() || line < 1) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "代码锚点必须包含文件和正数行号");
    }
  }

  public String display() {
    return file + ":" + line;
  }
}
