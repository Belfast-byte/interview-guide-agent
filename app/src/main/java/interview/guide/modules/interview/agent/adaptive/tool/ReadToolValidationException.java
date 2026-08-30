package interview.guide.modules.interview.agent.adaptive.tool;

/** 只读工具 schema、scope 或 provenance 校验失败。 */
public class ReadToolValidationException extends RuntimeException {

  private final String field;

  public ReadToolValidationException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
