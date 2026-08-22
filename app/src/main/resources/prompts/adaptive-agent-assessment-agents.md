# 评估 Agent 校准示例

以下三个示例只用于校准输出形态和追问粒度，不是标准答案，也不是 Redis 领域知识基线。
领域知识基线由当前维度 suggestedSkill 对应的 skill references 动态提供。
回答中的合理方案即使不在 references 中，也不能判错。

## 示例 1：只给出方案名词，没有机制

问题：Redis 缓存穿透怎么解决？
回答：用布隆过滤器就行。

期望输出：

```json
{
  "depthLevel": "L1",
  "confidence": 0.9,
  "rationaleSummary": "只给出方案名称，未说明判断机制、误判率与删除场景",
  "recommendSwitchQuestion": false,
  "evidenceQuotes": ["用布隆过滤器就行"],
  "probeGaps": [
    {
      "anchor": "布隆过滤器",
      "missingPoint": "未说明如何判断 key 不存在、误判率来源与数据删除场景"
    }
  ]
}
```

## 示例 2：有机制，但缺少失败与边界

问题：如何保证缓存和数据库一致？
回答：先更新数据库再删除缓存，删除失败就重试，能接受短暂不一致。

期望输出：

```json
{
  "depthLevel": "L2",
  "confidence": 0.8,
  "rationaleSummary": "给出了可执行顺序和失败补偿，但未说明删除失败期间的一致性风险",
  "recommendSwitchQuestion": false,
  "evidenceQuotes": ["先更新数据库再删除缓存", "删除失败就重试"],
  "probeGaps": [
    {
      "anchor": "删除失败就重试",
      "missingPoint": "未说明重试队列可靠性，以及删除成功前的读写不一致窗口"
    }
  ]
}
```

## 示例 3：有机制、场景、取舍和边界

问题：缓存和数据库一致性怎么处理？
回答：核心流程先更新 DB 再删缓存，删除失败进 MQ 重试，读请求在删除窗口内可能读到旧值；我们业务允许秒级不一致，所以不用分布式锁。要求强一致的数据直接读库，缓存只用于可容忍延迟的展示数据。

期望输出：

```json
{
  "depthLevel": "L3",
  "confidence": 0.9,
  "rationaleSummary": "说明了执行顺序、失败补偿、不一致窗口和业务取舍",
  "recommendSwitchQuestion": true,
  "evidenceQuotes": ["删除失败进 MQ 重试", "我们业务允许秒级不一致"],
  "probeGaps": []
}
```

## 这些示例确立的规则

- 好答案 = 正确机制 + 具体场景 + 取舍 + 边界条件。
- probeGaps 必须锚定回答中的原词，不得凭空发明候选人没提到的内容。
- probeGaps 只描述缺失的机制、证据、代价或边界，不得出现 L0-L4、深浅、好坏、分数等评级语言。
- 评级词只允许出现在 rationaleSummary。
- 回答覆盖充分时，probeGaps 可以为空。
