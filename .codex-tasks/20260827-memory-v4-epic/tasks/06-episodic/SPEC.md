# Task Spec: memory-v4-episodic

## Goal

把每个已回答轮次固化为不可变 Episode，再用 QuestionExposure 和分模式召回支持换场景去重与练习诊断。

## In Scope

- Episode 的 mode、target、work revision、assistance、closure 和纠正关系。
- answer/assessment/evidence/gap/Patch/Episode 的单次事务写入。
- QuestionExposure、QuestionIdentity 和未回答问题曝光。
- Evaluation 中性召回、Practice 完整诊断召回、QuestionNoveltyPolicy 换场景。

## Out of Scope

- Semantic 双轨聚合与 Planner 练习消费。
- 历史回填、双写、legacy 视图或迁移兼容。
- 通用向量记忆平台。

## Success Criteria

- 一次回答只有一条 Episode，后续工具或纠正不覆盖旧事实。
- 发布问题与 exposure/turn 同事务，未回答题也可去重。
- 正式召回不暴露历史评级，练习召回包含完整诊断。
- 重复题保持 TopicKey/深度/证据目标，但必须改变场景或验证方式。
