# Java 改错题验证记录（2026-09-12）

本记录区分确定性检查、真实数据库约束、真实模型语义抽样及尚未完成的应用链路验证。全部模型输入均为脚本中的合成 JD、合成简历和库存代码，没有读取业务数据库或真实个人材料；产物不含凭据或模型推理正文。

## 已完成

- `NullableModelSchemaTest`、`DecisionContextProjectionTest`、严格输出入口、Planner 和 Decision 回归测试通过，定向 Gradle 执行 16 秒。生产 schema 明确允许任务引用、任务联合分支、WorkingMemory 可选单值、评估 `codeReview` 和引用 `startOffset` 为 null / 省略；必填题型、引用数组及其元素没有因此放宽。
- PostgreSQL 12.22 原生临时集群验证 19 项增量约束全部通过，进程退出码 0，启动、执行与关闭约 0.9 秒。不是 H2 模拟，也没有连接业务数据库。
- 以下四个真实响应通过修正后的生产 JSON schema。三个评估同时通过 Python 原文定位检查与 Java 真实 `strictConverter`、`CodeRepairReview.validate`、`SourceQuote.resolve` 回放；首题通过真实 `InterviewPlan.decide` 和 `InitialQuestionProposal.toDecision`。首题的人工质量发现单列于下文，结构通过不代表题目完全正确。

| 合成样例 | 输出 tokens | 耗时 | finish 原因 | 验证结果 |
| --- | ---: | ---: | --- | --- |
| 首题 Java 任务 | 5150 | 81.17 秒 | `end_turn` | 新代码任务、单一合法主题、字段和根引用合法；存在人工质量问题 |
| 正确原子条件扣减且拒绝不足 | 2266 | 30.43 秒 | `end_turn` | C1、C2 均 `SATISFIED`；3 条 Evidence/gap 引用精确定位 |
| 错误的单实例 `synchronized` | 2773 | 46.99 秒 | `end_turn` | C1 `NOT_SATISFIED`，C2 `SATISFIED`；2 条引用精确定位 |
| 条件扣减但忽略返回值 | 1482 | 23.04 秒 | `end_turn` | C1 `SATISFIED`，C2 `NOT_SATISFIED`；2 条引用精确定位 |

实际请求和返回的模型均为 `deepseek-v4-pro`，通过环境提供的 DeepSeek Anthropic 兼容端点调用，显式 `output_config.effort=low`、`max_tokens=8192`。这是**环境 provider 的模型质量抽样**，不是应用已配置 provider、Spring AI HTTP 客户端或答案事务的真实端到端验证。应用 `.env` 指向的 provider YAML/env 文件在本环境不存在。

应用 application.yml 默认 Planner 60 秒、Assessment 30 秒（未绑定配置时属性类默认分别为 30 秒和 20 秒）；首题及两个评估样例超过 YAML 默认截止时间，不能宣称当前配置下整条真实应用链路通过。此次未修改业务超时配置。有限样本也不证明 8192 tokens 对所有生成场景都足够。

## 真实发现与修复边界

1. **原生产 schema 与空值契约矛盾。** 原生成器把 `codeTaskTurnIndex`、`startOffset` 等声明为必填且不允许 null。已在真实可空字段使用生成器支持的 `@Schema(nullable=true, requiredMode=NOT_REQUIRED)` 修复，并用确定性测试检查 null、可省略和仍必填的字段；未填充默认值、修改模型 JSON 或放宽未知字段检查。
2. **原引用偏移样例失败。** 旧 schema 的一次正确修复评估写入了未命中原文的偏移，被精确校验拒绝。`rejected-source-offset.json` 保留该失败响应。修正可选偏移 schema 后的对应样例通过，不能由一次通过推断模型不再犯错。
3. **默认高推理可耗尽预算。** 一次未显式设置 effort 的首题请求耗尽 8192 tokens，返回 `max_tokens` 且可见正文为空，耗时 122.21 秒，保存在 `rejected-high-token-budget.json`。后来显式 low 的样例未截断。DeepSeek 文档说明默认推理为 high，Anthropic 接口支持 `output_config.effort`；此处使用官方 API 模型名，没有把 Claude 客户端的 `[1m]` 装饰后缀发送给服务端。[思考模式说明](https://api-docs.deepseek.com/guides/thinking_mode/)、[Anthropic 兼容接口](https://api-docs.deepseek.com/guides/anthropic_api/)
4. **首题人工质量问题未被结构校验证明正确。** 归档首题可见题面没有复述修复答案，业务背景及假设也明确与库存场景有关，但 JPQL 使用 `i.id`，对应实体只有 `productId`；该额外符号错误未被 `reviewGuide` 覆盖。幂等设施的接口和事务语义不够明确，隔离级别使用“默认（通常 READ_COMMITTED）”。已补充生产 Planner/Decision 提示词，要求具体前提、依赖契约和参考覆盖所有刻意缺陷。
5. **主题唯一规则。** 更早的首题响应曾用不同维度名称重复同一 Skill/Focus 组合。生产 `InterviewPlan` 已禁止该情形；提示词现明确要求同一组合只能出现一次，smoke 也增加这一断言。归档首题只有一个主题并通过真实领域校验。

用户明确批准后，最后一版出题提示词已完成一次真实复测，结果见下方。结构通过，但人工质量仍有缺口，不标记为质量通过；此前失败产物继续保留。

## 外发审批记录（已获得批准）

此前仅重测首题的外发请求被自动审批拒绝；随后用户明确回复“允许”，授权下述一次请求。此前拒绝理由如下：

> 该操作会把仓库中的生产 system prompt、安全指令及 schema/格式内容发送到外部 DeepSeek 服务；这些内部实现细节属于敏感数据，用户未明确授权向该具体外部目的地披露，不能仅因输入样例是合成数据而放行。

本次获准动作是向 **DeepSeek，`https://api.deepseek.com/anthropic/v1/messages`** 发送一次合成首题请求，模型 `deepseek-v4-pro`、推理 `low`、输出预算 8192。凭据只由环境读取，不进入日志或仓库。请求内容具体包括：

- `app/src/main/resources/prompts/adaptive-agent-planner-system.st`，最新出题要求。
- `app/src/main/resources/prompts/adaptive-agent-planner-user.st`，替换为脚本内合成 JD、简历和 Skill 目录后的用户提示词。
- `StructuredOutputInvoker.strictConverter(PlanProposal.class)` 生成的 JSON schema 和 `getFormat()` 格式指令，涉及首题代码任务契约。
- `app/src/main/java/interview/guide/common/ai/PromptSecurityConstants.java` 中的 `ANTI_INJECTION_INSTRUCTION`。

此请求不发送真实 JD/简历、业务数据库记录或凭据正文。拒绝后没有绕过审批；用户明确批准后，仅向上述目的地执行了一次请求。审批阻断已解除。

## 获准后的首题复测

在 `55b8f16` 版本上调用一次 `deepseek-v4-pro`，推理 `low`、预算 8192，返回 `end_turn`。输入 2157 tokens、输出 2415 tokens，耗时 **39.90 秒**。使用最新生产提示词、生成器格式指令及安全指令，未修补响应。

- JSON schema、主题唯一性、新根任务检查通过；真实 `strictConverter`、`InterviewPlan.decide` 和 `InitialQuestionProposal.toDecision` 回放通过。
- 人工审阅确认上一样例的 JPQL 字段错误没有重现；原子扣减接口、返回值和 READ_COMMITTED 隔离级别已明确，也不再要求未定义的幂等设施。
- **质量尚未通过**：没有声明数量已校验为正数或 SKU 保证存在，负数量导致库存增加的路径不在当前参考中；两个 Repository 是否参与同一数据源/事务管理器下的事务仍不明确。
- 参考的机制说明也需更准确：给定先读、校验、写回序列在正数量下可能丢失扣减、造成超卖，不能直接据此推断写出负库存。
- 本次 39.90 秒低于 YAML 的 Planner 60 秒默认值，但只是环境 provider 的一次观测，没有补齐应用端到端验证，也不改变此前评估超时样例的结果。

原始响应、合成输入、脚本结果、Java 校验输出及人工审阅结论保存在 `model-samples/2026-09-12/approved-planner/`。未编译或运行生成的业务代码。CR-08 继续保留未完成状态，原因为题目质量及应用模型链路验证缺口，已不再是外发审批或 PostgreSQL 环境阻断。

## Docker PostgreSQL 16 复测

Docker 启动后，使用本地 `pgvector/pgvector:pg16` 镜像创建临时实例，实际版本 PostgreSQL 16.14。端口只绑定本机随机端口，测试凭据随机生成；未向开发数据库写入，临时容器及其数据卷已清理。

第一次真正执行 `PostgresAgentSchemaMigrationTest` 发现测试隔离方式不符合历史迁移：测试使用随机 schema，而 V20260723 显式检查 `public.vector_store`，导致重复建表。已将测试改为每条路径使用独立临时数据库、沿用生产 `public` schema，历史迁移文件未改写。测试连接账号需要 CREATEDB 权限及扩展安装权限。

定向检查 16.92 秒通过；随后带 PostgreSQL 环境的完整自适应模块和 StrictInvoker 联合回归 **338 项全部通过，0 失败、0 跳过，33.59 秒**。两次 Gradle 执行均使用 `timeout 60s`：

- 空库执行全部 **57 个迁移**到 V20261007，Flyway 校验和全实体 JPA validate 通过。
- 升级路径先执行 46 个迁移到 V20260926，再执行剩余 **11 个迁移**到 V20261007，Flyway 校验和全实体 JPA validate 通过。

复现入口为已有 `PostgresAgentSchemaMigrationTest`：设置隔离 PostgreSQL 实例的 `POSTGRES_SCHEMA_TEST_URL`、`POSTGRES_SCHEMA_TEST_USER`、`POSTGRES_SCHEMA_TEST_PASSWORD`，运行 `timeout 60s ./gradlew :app:test --tests '*PostgresAgentSchemaMigrationTest' --no-daemon`。测试会自动创建和删除两条路径的临时数据库。

本次没有执行新的外部模型请求。应用 `.env` 引用的 provider 配置文件仍不存在，Docker 启动本身未补齐应用模型调用配置；模型质量与真实应用端到端缺口仍按前文保留。

## 产物与复现

原生数据库验证：`verify_code_repair_migration.py`、`code_repair_migration.sql`。SQL 使用迁移前四张表的相关列 fixture，实际加载 V20261007；覆盖旧文字/沙箱值不变、历史 locator null、根自引用、同场引用、跨场外键拒绝、错误组合和仅代码答案。该脚本本身不是完整历史 Flyway 链或 PostgreSQL 16 的演练；完整链已在下述 Docker 复测中补齐。根与引用链的语义检查由应用边界负责。

```bash
LD_LIBRARY_PATH=/tmp/code-repair-pg-packages/runtime/usr/lib/x86_64-linux-gnu \
python3 scripts/verification/verify_code_repair_migration.py \
  --bin-dir /tmp/code-repair-pg-packages/runtime/usr/lib/postgresql/12/bin \
  --share-dir /tmp/code-repair-pg-packages/runtime/usr/share/postgresql/12
```

模型产物保存在 `model-samples/2026-09-12/`：四份原始可见响应、完全合成的 inputs、当前 schema、本地校验结果及两个真实失败样例。`code_repair_model_smoke.py` 要求 Python `jsonschema`，每项失败显式写入 summary 并以非零状态退出；无自动重试、JSON 清理或输出修补。运行真实请求前应满足上文具体外发审批。

已编译应用及运行时依赖组成 `APP_RUNTIME_CLASSPATH` 后，可只在本地导出 schema 和回放响应：

```bash
java -cp "$APP_RUNTIME_CLASSPATH" scripts/verification/ExportCodeRepairSchema.java /tmp/code-repair-schema
java -cp "$APP_RUNTIME_CLASSPATH" scripts/verification/ValidateCodeRepairModelSample.java \
  scripts/verification/model-samples/2026-09-12/correct.json \
  scripts/verification/model-samples/2026-09-12/inputs.json
```

`ValidateCodeRepairModelSample.java` 对评估执行真实引用和审阅校验，对首题执行真实计划和提案校验；不会重新调用模型。环境 provider 的请求示例已保留在脚本参数中，未写入任何固定 API key。
