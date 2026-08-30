package interview.guide.modules.interview.agent.adaptive.core.session;

/** 下一题实际采用的 rubric 条目与不可变版本。 */
public record AdoptedRubricSource(String reference, String entryId, String version) {

  private static final String PREFIX = "rubric:";

  public static AdoptedRubricSource fromReference(String reference) {
    int versionSeparator = reference.lastIndexOf('@');
    if (!reference.startsWith(PREFIX) || versionSeparator <= PREFIX.length()
        || versionSeparator == reference.length() - 1) {
      throw new IllegalArgumentException("rubric provenance 引用格式非法");
    }
    return new AdoptedRubricSource(
        reference,
        reference.substring(PREFIX.length(), versionSeparator),
        reference.substring(versionSeparator + 1)
    );
  }
}
