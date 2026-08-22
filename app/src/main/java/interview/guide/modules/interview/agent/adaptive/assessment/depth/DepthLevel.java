package interview.guide.modules.interview.agent.adaptive.assessment.depth;

/**
 * 深度等级枚举。
 */
public enum DepthLevel {
  L0(
      "无证据",
      "不会、跑题，或只背概念但答非所问",
      "换题或降难度验证基础"
  ),
  L1(
      "概念复述",
      "能复述定义，但没有个人经验",
      "追问应用场景"
  ),
  L2(
      "应用描述",
      "能说明自己做过什么",
      "追问决策理由和取舍"
  ),
  L3(
      "权衡分析",
      "能比较方案、代价和边界条件",
      "追问反例或极端场景"
  ),
  L4(
      "迁移洞察",
      "能归纳方法、识别反例并跨场景迁移",
      "当前维度可提前完成"
  );

  private final String meaning;
  private final String typicalPerformance;
  private final String actionTendency;

  DepthLevel(
      String meaning,
      String typicalPerformance,
      String actionTendency
  ) {
    this.meaning = meaning;
    this.typicalPerformance = typicalPerformance;
    this.actionTendency = actionTendency;
  }

  public String meaning() {
    return meaning;
  }

  public String typicalPerformance() {
    return typicalPerformance;
  }

  public String actionTendency() {
    return actionTendency;
  }

  /**
   * 量规条目文本，注入评估上下文供模型按级评定。
   *
   * @return 单行量规描述
   */
  public String rubricLine() {
    return name() + "（" + meaning + "）：" + typicalPerformance + "；行动倾向：" + actionTendency;
  }
}
