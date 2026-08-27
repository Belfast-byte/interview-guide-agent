# 候选人 Provider 与自适应面试历史 Spec

> 维护：Agent；上游设计决策以 `docs/design/` 为准。
>
> 状态：待实施
>
> 权威输入：[认证与权限边界设计](./02-auth-permission.md)、[自适应文本面试设计](./10-text-interview.md)、2026-08-22 需求确认
>
> 最后更新：2026-08-22

## 1. 实现目标

为候选人补齐自适应面试的两个必要闭环：

1. 候选人能够维护自己独有的 LLM Provider，配置文本模型和嵌入模型，并明确选择默认模型；Adaptive Agent 的文本调用使用候选人选择的文本 Provider。
2. 候选人能够分页查看自己的 Adaptive Agent 历史会话，并从历史列表继续进行中的面试或查看已完成面试的问答与评估报告。

成功标准：Provider 数据与默认配置严格按候选人隔离；Adaptive 新会话不再接受任意 Provider 字符串；历史列表只返回当前认证候选人的非租户 Adaptive 会话；所有约束失败均以明确业务异常暴露。

## 2. 范围与不做清单

本期不做：

- 用户嵌入模型不接入向量化、检索、题库或知识库消费者链路，仅保存和展示配置。
- 不迁移、不展示旧 Agent Loop/V1 会话。
- 不删除或私有化现有系统 Provider；它们继续服务平台级共享链路，候选人不可见。
- 不实现 Provider 配置版本管理。
- 不允许编辑或删除进行中会话正在使用的 Provider。
- 不提供历史会话删除、搜索或筛选。
- 不新增 Provider 类型、协议或自研加密机制，复用现有 OpenAI 兼容配置、API Key 加密和连接测试能力。

## 3. 功能点总览

| ID | 功能点 | 主要结果 |
|---|---|---|
| PF-1 | 用户私有 Provider 数据模型 | 系统配置与候选人配置隔离 |
| PF-2 | Provider 管理接口与页面 | 新增、编辑、测试、删除 |
| PF-3 | 默认文本/嵌入模型 | 每个用户各一个默认角色 |
| PF-4 | Adaptive 文本模型接入 | 创建会话时选择并锁定用户 Provider |
| PF-5 | Provider 引用约束与异常 | 默认或活动引用时拒绝变更 |
| HI-1 | 本人历史会话分页接口 | 仅返回本人 Adaptive 会话摘要 |
| HI-2 | 历史面试页面 | 继续面试或查看报告 |

## 4. 功能点规格

### PF-1 用户私有 Provider 数据模型

#### 1. 要做什么

- 在现有 Provider 数据中建立明确所有权：系统 Provider 无候选人归属，用户 Provider 关联唯一 `candidateId`。
- 用户 Provider 使用全局唯一内部 ID；展示名称只要求在同一候选人内唯一，不同用户可以重名。
- 每个用户 Provider 必须配置文本模型，可选配置嵌入模型。
- 保留现有 Base URL、加密 API Key、文本模型参数、嵌入模型名称和维度等已有字段。
- 为用户默认配置建立一人一行的设置，分别引用默认文本 Provider 和默认嵌入 Provider。
- Adaptive 会话保存 Provider 内部 ID，并保存 Provider 名称、文本模型名称快照，保证 Provider 删除后历史仍可解释。

#### 2. 影响哪些

- Flyway：Provider 所有权、用户默认设置、会话 Provider 快照、唯一约束与索引。
- `modules/llmprovider/model`、`repository`、`service`：所有权模型及归属查询。
- `adaptive/persistence/session`：会话 Provider ID 和快照映射。
- `LlmProviderRegistry`：继续使用全局唯一内部 ID 作为缓存键，避免跨用户串用缓存。

#### 3. 如何判断完成

- 用户 A、B 可以创建同名 Provider，内部 ID 不同。
- 用户 A 无法查询、修改、测试或删除用户 B 的 Provider。
- 用户 Provider 的 API Key 仅以密文落库，响应只返回掩码。
- 系统 Provider 不出现在任何候选人接口响应中，原平台共享链路仍能读取系统 Provider。
- 已完成会话在其 Provider 删除后仍能读取原 Provider 名称和文本模型快照。

### PF-2 Provider 管理接口与页面

#### 1. 要做什么

- 新增候选人专用 `/api/me/llm-providers` 接口，由 `AuthenticatedUser` 提供 `candidateId`，请求不得携带候选人 ID。
- 支持列表、新增、编辑、文本模型连接测试和删除。
- 编辑时 API Key 留空表示保留原密钥；提供新值时重新加密保存。
- 前端新增 `/providers` 页面，展示名称、服务地址、文本模型、可选嵌入模型、默认角色和 Key 掩码。
- 页面提供新增、编辑、测试连接、删除、设置默认文本模型、设置默认嵌入模型操作。
- 嵌入模型区域固定展示“仅保存配置，暂未接入业务链路”。连接测试仅测试文本模型。

#### 2. 影响哪些

- 后端 `modules/llmprovider/controller`、`service`、请求/响应 DTO 与安全路径配置。
- 前端 `api/`、`types/`、Provider 页面、路由常量、`App.tsx` 和侧边导航。
- 复用现有 `ApiKeyEncryptionService` 和 Provider 文本连接测试，不新增加密或探活协议。

#### 3. 如何判断完成

- 用户可从页面完成新增、编辑、测试、删除全流程，刷新后数据一致。
- 编辑时留空 API Key 不会覆盖原密钥；返回值和页面不出现明文 Key。
- 重名、归属错误、连接失败、默认引用和活动会话引用均显示后端业务错误原文。
- 配置嵌入模型不会创建 EmbeddingModel、执行嵌入请求或改变平台默认嵌入模型。

### PF-3 默认文本/嵌入模型

#### 1. 要做什么

- 每个候选人最多设置一个默认文本 Provider和一个默认嵌入 Provider。
- 同一个 Provider 可以同时承担两个默认角色；设置新默认值时原默认值被替换。
- 只有配置了嵌入模型的 Provider 才能设为默认嵌入 Provider。
- 默认 Provider 禁止删除，用户必须先切换默认角色。
- 删除非默认 Provider 不改变默认设置。

#### 2. 影响哪些

- 用户默认设置 Entity、Repository、Service 与候选人 Provider API。
- Provider 页面默认徽标、设置按钮和操作状态。
- ErrorCode 增加或复用明确的 Provider 默认配置错误。

#### 3. 如何判断完成

- 任意时刻每个用户最多各有一个默认文本/嵌入 Provider。
- 两个用户设置默认值时互不影响。
- 无嵌入模型的 Provider 设置为默认嵌入时快速失败。
- 删除任一默认 Provider 时返回明确 `BusinessException`，数据不发生变化。

### PF-4 Adaptive 文本模型接入

#### 1. 要做什么

- Adaptive 创建页将自由文本 Provider 输入改为用户文本 Provider 下拉框。
- 页面加载时选中用户默认文本 Provider，同时允许为本次会话选择其他本人 Provider。
- 未设置默认文本 Provider 时禁止创建会话，显示 Provider 业务异常并提供 Provider 页面入口。
- 后端在首次 LLM 调用前一次性完成 Provider 存在性、归属和文本模型可用性校验。
- 新会话持久化所选 Provider 内部 ID、名称和文本模型快照；后续轮次使用该 Provider，不受默认值切换影响。
- 存量会话仍按其已持久化 Provider 读取；系统 Provider 不可用于创建新的候选人会话。

#### 2. 影响哪些

- `AdaptiveInterviewPage`、`adaptiveInterviewApi` 和创建请求类型。
- `AdaptiveInterviewController`、`AdaptiveInterviewApplicationService`、规划/面试/评估模型获取链路。
- `AdaptiveAgentSessionEntity`、迁移脚本和响应 DTO。
- 用户 Provider 查询服务与 `LlmProviderRegistry` 调用参数。

#### 3. 如何判断完成

- 无默认文本 Provider 时，前后端均不能创建会话，后端是最终约束来源。
- 默认 Provider 自动选中；显式选择其他 Provider 后，会话保存并使用该 Provider。
- 修改默认 Provider 不会切换已创建会话使用的 Provider。
- 伪造其他用户 Provider ID 时返回 Provider 不存在/不可用业务异常，不泄露其存在性。
- Adaptive 全部文本调用均使用会话保存的用户 Provider；嵌入链路调用次数保持不变。

### PF-5 Provider 引用约束与异常

#### 1. 要做什么

- Provider 被 `CREATED` 或 `IN_PROGRESS` Adaptive 会话引用时，禁止编辑和删除。
- Provider 被任一默认角色引用时，禁止删除。
- 仅被已完成会话引用且不再是默认值时允许删除。
- 所有失败使用 `BusinessException(ErrorCode.PROVIDER_XXX, "明确描述")`，不转换为静默默认值或通用运行时异常。
- 前端使用统一请求错误处理直接展示业务消息。

#### 2. 影响哪些

- Provider Service、Adaptive Session Repository 的归属合并查询。
- Provider 专用 ErrorCode、全局异常响应与前端错误展示。
- 数据库外键策略：历史快照不能因 Provider 删除而丢失；活动引用约束由业务查询明确执行。

#### 3. 如何判断完成

- 默认 Provider 删除失败，切换默认后才可删除。
- 活动会话 Provider 的编辑和删除均失败，原配置保持不变。
- 会话完成后，非默认 Provider 可以删除，历史详情仍正常。
- 所有拒绝场景返回 HTTP 200 + `Result.error(providerCode, message)`，页面展示相同业务语义。

### HI-1 本人历史会话分页接口

#### 1. 要做什么

- 新增 `GET /api/adaptive-agent-interviews/history?page=0`，固定每页 20 条，按 `createdAt DESC` 返回。
- `candidateId` 仅从认证主体获取；查询同时约束 `tenantId IS NULL`，排除 MCP 租户会话。
- 使用轻量摘要响应：`sessionId`、`status`、`currentTurn`、`maxTurns`、`jdSummary`、`createdAt`、`completedAt`。
- JD 摘要由后端确定性生成，最长 120 个字符；列表不得逐条加载计划、轮次、评估或报告。
- 增加候选人、租户标识和创建时间组合索引。

#### 2. 影响哪些

- `AdaptiveAgentSessionRepository` 分页归属查询与数据库索引迁移。
- Adaptive Persistence/Application Service 的只读列表方法。
- `AdaptiveInterviewController` 和独立 `AdaptiveInterviewSummaryResponse`。
- 安全边界与查询测试。

#### 3. 如何判断完成

- 返回结果只包含当前用户、`tenantId IS NULL` 的 Adaptive 会话。
- 记录严格按创建时间倒序，每页最多 20 条，分页元数据正确。
- 列表查询数量固定，不因返回记录数产生 N+1 查询。
- 旧 Agent Loop/V1 会话不会进入接口结果。

### HI-2 历史面试页面

#### 1. 要做什么

- 新增 `/interview-history` 页面和侧边导航入口。
- 页面展示创建时间、状态、轮次进度和 JD 摘要，支持分页、加载中、空数据和失败状态。
- 点击进行中会话进入现有 `/adaptive-interview/:sessionId` 并继续回答。
- 点击已完成会话进入同一详情页，自动加载现有问答记录和评估报告。
- 不复制详情和报告 UI，不新增第二套会话展示逻辑。

#### 2. 影响哪些

- 前端 `types/`、`api/adaptiveInterview.ts`、历史页面、路由常量、`App.tsx` 和 `Layout`。
- 复用 `AdaptiveInterviewPage`、`adaptiveInterviewApi.get()` 与 `getReport()`。

#### 3. 如何判断完成

- 登录用户可从导航进入历史页并翻页。
- 空列表、接口失败和加载过程有明确状态，不显示伪造数据。
- 进行中记录可继续作答；已完成记录可查看原问答和评估报告。
- 手工修改 URL 访问他人 sessionId 时仍由后端归属查询拒绝。

## 5. 实施顺序

1. PF-1：迁移和私有数据模型。
2. PF-2、PF-3：后端私有 CRUD、默认设置及 Provider 页面。
3. PF-4、PF-5：Adaptive 接线和引用约束。
4. HI-1：历史分页查询与接口。
5. HI-2：历史页面与详情复用。

每一步必须先通过自身后端测试或前端构建，再进入下一步；不得用系统 Provider 回退掩盖用户配置缺失。

## 6. 验收方式

### 6.1 自动验证

- 后端：`timeout 60s ./gradlew :app:test --no-daemon --console=plain`。
- 前端：`cd frontend && pnpm run build`。
- 数据迁移：空库启动成功；带现有系统 Provider 和 Adaptive 会话的数据升级成功。
- SQL/日志检查：API Key 明文不落库、不进入响应和日志。

### 6.2 产品验收流程

1. 用户 A 登录，创建两个 Provider，分别配置文本和可选嵌入模型。
2. 将 Provider A 设为默认文本和默认嵌入，确认页面徽标正确；确认嵌入模型标记为暂未启用。
3. 测试文本连接，编辑非活动 Provider，验证 API Key 留空时原 Key 保持。
4. 创建 Adaptive 面试，确认默认选中 A；另建一场并显式选择 B。
5. 面试进行中尝试编辑、删除其 Provider，确认显示明确业务异常。
6. 尝试删除默认 Provider，确认失败；切换默认且会话完成后删除，确认成功。
7. 进入历史页面，确认两场会话按时间倒序显示；继续进行中会话并查看已完成报告。
8. 用户 B 登录，确认看不到用户 A 的 Provider、默认设置和历史会话。

## 7. 关键测试场景

### 7.1 Provider 归属与安全

- 同一用户 Provider 名称重复失败；不同用户同名成功。
- 用户 A 对用户 B 的 ID 执行查询、编辑、测试、删除、设置默认均失败且不泄露存在性。
- 新增和更新 API Key 后数据库仅有密文；列表和详情仅返回掩码。
- 系统 Provider 不进入候选人列表，但平台共享嵌入链路仍可使用。

### 7.2 默认配置与嵌入边界

- 文本和嵌入默认值分别唯一，同一 Provider 可同时承担两者。
- 设置新默认值能原子替换旧值；跨用户互不影响。
- 无嵌入模型的 Provider 不能设为默认嵌入。
- 保存或设置用户嵌入模型时，不创建嵌入客户端、不调用嵌入 API、不改变系统默认嵌入配置。

### 7.3 Adaptive Provider 生命周期

- 无默认文本 Provider 时创建失败；设置默认后创建成功。
- 显式选择本人非默认 Provider 成功；伪造他人或系统 Provider ID 失败。
- 会话保存正确 ID、名称和模型快照；切换默认不影响已有会话。
- 活动会话引用时编辑/删除失败；完成后非默认 Provider 删除成功。
- Provider 删除后完成会话仍可加载问答和报告。

### 7.4 历史会话隔离与分页

- 混合插入用户 A、用户 B、MCP tenant 和旧 V1 会话，只返回用户 A 的非租户 Adaptive 会话。
- 超过 20 条时分页边界、总数和时间倒序正确，无重复或遗漏。
- 历史列表使用轻量查询，不加载轮次、计划和报告集合。
- 进行中记录跳转后可继续回答；完成记录跳转后自动加载报告。
- 直接访问其他用户 sessionId 返回会话不存在业务错误。

## 8. 完成定义

- PF-1～PF-5、HI-1～HI-2 的完成标准全部满足。
- 新增迁移可在空库和存量库执行。
- Provider/Adaptive/历史相关测试全绿，前端生产构建通过。
- 产品验收流程逐条演示通过。
- 文档、接口、页面不存在“用户嵌入模型已投入业务使用”的误导描述。
