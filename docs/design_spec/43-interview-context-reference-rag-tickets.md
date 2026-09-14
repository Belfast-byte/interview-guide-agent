# 出题上下文与参考 RAG 实施 tickets

日期：2026-09-13。上游：[42 号规格](./42-interview-context-reference-rag-spec.md)。状态：代码实现与离线回归完成，真实 RAG 验收待完成。

| Ticket | 工作 | 验收 | 状态 |
|---|---|---|---|
| CTX-1 | 预设 Skill 增加精简 decision.md；替换出题参考装配与内部字段 | 默认输入不含专业参考；资源完整；Planner/Assessor 不改 | 已实现，原会话出题通过 |
| CTX-2 | 专业参考按实际资源去重、分块，复用 VectorStore 建索引；新增 reference_search | 限定本场目标；更新删除可同步；失效索引拒绝；无持久化采用来源 | 已实现，离线测试通过；真实 Embedding 待验收 |
| CTX-3 | 工具结果限量、决策投影去重与剩余输入预算；分项日志 | 完整片段选择；必需事实不裁剪；不记录正文；最终预算检查仍生效 | 已实现，离线测试通过 |
| CTX-4 | 边界测试与原失败会话真实模型重放 | 原第 5 轮正常决策，补充后续轮次；不提交新评分或下一题 | 原第 5 轮通过；RAG 与合成后续轮次待验收 |

## 实施边界

- Planner、Assessor 输入与正式量规快照链路保持原样。
- 不改历史窗口，不新增任务队列、记忆表或通用检索框架。
- 新工具类承接专业参考的目标范围与无评分来源语义；索引类负责 classpath 资源到现有向量库的同步，无专属事实表。
- 用户原有 `maxInputTokens=20_000` 修改保留，不通过调大上限验收。

## 验收记录

### 已完成

- `./gradlew :app:test --no-daemon`：最终完整回归 663 项，612 通过、51 跳过，无失败；跳过项包括默认关闭的真实模型测试。
- 新增资源完整性、索引更新删除、失效命中、目标范围、无命中与故障区分、完整片段预算、跨 Observation 去重测试。
- 原会话第 5 轮重放：Assessor 估算输入保持 10,976 token，真实调用成功；Interview Agent 输入 12,837 token，成功返回 `Ask`，低于当前 20,000 上限。
- 出题分项：岗位说明 224 token（原固定参考 8,756）、历史 5,720、覆盖情况 2,158、系统提示与 Schema 4,216；本次无工具结果。历史与覆盖部分随真实评分提案略有变化，不能将所有差值归因于静态代码修改。
- 通过真实业务服务重放，不调用评分及下一题提交事务。显式启用的测试另设数据库计数与会话状态不变断言。

### 索引运行方式

- `SkillReferenceIndex` 在应用就绪时同步 classpath 资料到现有 `vector_store`。每次启动比较原文，只向量化新增或变化片段，删除本类型的失效片段；无需新表或迁移。
- 同资源跨分类去重，目标映射由当前 Skill 配置解析；检索同时限定文档类型和当前目标可用资源，并核对命中的原文。
- 章节内分块约 450 token；默认最多返回 3 个片段，结果正文及元数据预算为 2,000 token。下一次出题准备时按实际总输入再次移除可选片段，并预留 256 token；必需事实仍由最终预算检查决定是否可发送。
- 初始化失败明确记录 `skill_reference_index_unavailable`，工具返回错误；修复 Embedding 配置后重启可同步，不静默回退成全量参考。

### 真实 RAG 验收的当前限制

本地全局 Embedding Provider `siliconflow` 没有可用密钥，索引初始化在发送前报错 `This request requires apiKey or workloadIdentity`。这不影响已成功的原会话出题重放，但未完成真实参考召回及携带参考的后续模型验证。

测试账号存在同站点 SiliconFlow Provider。测试支持显式指定它，只在进程内借用客户端凭证，保持全局向量模型和维度不变，不修改 Provider 数据库配置。自动审批要求对该凭证用于向量化仓库资料作单独确认；已向用户请求，未获确认前不执行。

### 可重复执行的真实测试

`ReferenceRagLiveReplayTest` 默认跳过；仅在明确授权并设置以下环境变量时启用：

```text
REFERENCE_RAG_LIVE=true
REFERENCE_RAG_EMAIL=<已授权的测试账号>
REFERENCE_RAG_SESSION=<原会话 ID>
REFERENCE_RAG_TURN=5
# 可选：全局配置缺少凭证时，明确指定同站点、属于该账号的 Provider
REFERENCE_RAG_EMBEDDING_CREDENTIAL_PROVIDER=<Provider ID>
```

运行 `./gradlew :app:test --no-daemon --tests '*ReferenceRagLiveReplayTest' --info`。本地需将应用所需环境变量注入测试进程；不在文档或日志中打印凭证。该测试先重放原答案，再显式读取相关参考并交给真实出题模型，最后追加三轮合成上下文检查增长；合成轮次不是原会话的正式续答。
