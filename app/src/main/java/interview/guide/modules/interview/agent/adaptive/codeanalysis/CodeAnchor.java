package interview.guide.modules.interview.agent.adaptive.codeanalysis;

public record CodeAnchor(String file, int line) {

  public String display() {
    return file + ":" + line;
  }
}
