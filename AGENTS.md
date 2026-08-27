# AI Interview Platform Agent Rules

Spring Boot 4.1.0 + Java 21 + Spring AI 2.0.0 + React 面试平台。

本文件是跨工具 Agent 入口，只放长期有效、代码里不容易直接推断、猜错会影响结果的规则。更细的目录规则放在 `.claude/rules/`，需要时再读取。

# 行为规则
1.不要假设。不要隐藏困惑。主动暴露权衡取舍。
2.只写解决当前问题的最小代码，不做任何推测性功能。
3.只修改必须改的地方，只清理自己产生的问题。
4.明确定义成功标准，验证通过前持续迭代。
5.能抄不造：优先复用框架能力和本仓库已有实现；没有内部实现时，优先参考 GitHub 上成熟的开源实现并裁剪，不自行设计协议、格式和机制。
6.及时提交：每个合适的改动完成后立即按主题拆分提交，不积攒大量未提交文件；提交前确保编译/测试通过。
## 反过度工程
- 信任内部代码、框架、数据库约束和编译期保证；只在系统边界（用户输入、外部 API、网络）做校验，同一约束只校验一次，不为「不可能发生」的场景写防御代码。
- 绝不吞掉错误；优先快速失败，而不是掩盖问题。
- 禁止重复造轮子：加解密、哈希、JWT、限流、重试等一律用框架/JDK/仓库现有组件，引入新依赖前先确认现有能力做不到。
- 不做没有真实使用者的角色、配置项、扩展点和预留抽象；只写解决当前问题的最小代码。
- 落地细则见 `.claude/rules/backend.md`「Minimal Implementation」。

## Commands

```bash
./gradlew :app:compileJava
./gradlew :app:test --no-daemon
./gradlew :app:bootRun
```

```bash
cd frontend && pnpm run dev
cd frontend && pnpm run build
```

```bash
docker compose -f docker-compose.dev.yml up -d
```

## Project Structure

- `app/src/main/java/interview/guide/common/`: 通用能力，包括限流、AI 调用、异步模板、配置、异常、统一响应。
- `app/src/main/java/interview/guide/infrastructure/`: 技术基础设施，包括文件、导出、Redis、MapStruct 映射。
- `app/src/main/java/interview/guide/modules/`: 业务模块，每个模块自包含 MVC 分层。
- `app/src/main/java/interview/guide/modules/interview/agent/adaptive/`: 自适应面试 Agent，顶层按职责分包（`api`/`application`/`core`/`runtime`/`role`/`tool`/`planning`/`memory`/`assessment`/`persistence`/`algorithm`/`codeanalysis`/`mcp`/`observability`）；大模块内部再按职责划二级子包（如 `persistence.session`、`assessment.depth`），子包划分与依赖方向见 `docs/design_spec/20-implementation-modules.md` §3.2，细则见 `.claude/rules/interview-agent.md`。
- `app/src/main/resources/prompts/`: StringTemplate Prompt 模板。
- `frontend/src/`: React 前端页面、组件、API 客户端和类型定义。
- `docs/`: 文档中心，入口 `docs/README.md`。`docs/design/` 由用户主导，存放自然语言的框架性设计；`docs/design_spec/` 由 Agent 维护，存放技术规格、计划和 tickets；`docs/archive/` 存放历史文档。

## Architecture

- 后端遵循 `Controller -> Service -> Repository`，Controller 只做路由、校验和委托。
- Service 承担业务编排；`@Transactional` 只放 Service 层，并保持事务范围最小。
- Repository 继承 Spring Data JPA `JpaRepository`，自定义查询用方法名或 `@Query`。
- 基础设施能力优先放在 `common/` 或 `infrastructure/`，不要散落到业务 Service。
- 对外响应统一使用 `Result<T>`，禁止直接返回 Entity。

## Backend Rules

- 业务异常必须使用 `BusinessException(ErrorCode.XXX, "描述信息")`。
- 全局异常处理器返回 HTTP 200 + `Result.error(code, message)`。
- 请求体优先用不可变 `record`，命名后缀使用 `XxxRequest` / `XxxResponse` / `XxxDTO` / `XxxEntity`。
- Entity 到 DTO/Response 的映射优先使用 MapStruct。
- 使用构造器注入，优先配合 Lombok `@RequiredArgsConstructor`。
- 代码使用 2 空格缩进、无通配符导入、避免内联全限定类名。
- 日志使用 SLF4J 占位符，异常必须作为最后一个参数传入。

## AI And Async Rules

- 获取聊天模型统一走 `LlmProviderRegistry.getChatClientOrDefault(provider)`。
- 结构化输出统一走 `StructuredOutputInvoker`，不要在业务代码里复制重试逻辑。
- Prompt 模板放在 `resources/prompts/`，使用 StringTemplate `.st`。
- LLM、S3、外部 HTTP 调用不得放在数据库事务内。
- Redis Stream 生产/消费使用 `AbstractStreamProducer` / `AbstractStreamConsumer` 模板。
- 异步处理前先校验实体是否存在；实体已删除时 ACK 丢弃。
- 限流使用可重复 `@RateLimit`，不要手写散落的 Redis 限流逻辑。

## Interview Agent Rules（自适应面试 Agent）

- 产品与架构意图以 `docs/design/` 为准，Agent 技术规格在 `docs/design_spec/`，代码与测试是当前运行事实。改 adaptive 包前先读相关设计和规格；冲突必须显式暴露，不得静默改写 `docs/design/`。
- 依赖方向：`api → application → {core, runtime}`，`role`/`tool`/`planning`/`memory`/`assessment`/`persistence` 只依赖 `core`；`core` 是纯领域内核，禁止 import Spring AI/JPA/Redis/Web。
- 模型建议、代码裁决：状态迁移、轮次上限、计划轮次分配由代码确定性裁决（`AdaptiveInterviewSession`、`InterviewPlan.decide`），模型输出只是提案；证据与锚点必须逐字命中回答原文或真实分析产物。
- ReAct 循环统一走 `BoundedReActRuntime`（步数/工具数/deadline 三重预算）；工具必须经 `ToolGateway` 白名单执行，禁止模型网关自动注册工具。
- 「外部调用 → 裁决 → 落库」的串联只发生在 `application` 层；写库统一走 `AdaptiveInterviewPersistenceService`（短事务 + `@Version` 乐观锁），LLM/沙箱/外部 HTTP 调用在事务外。
- 会话状态全部存 PostgreSQL；Redis 只用于判题、代码分析等异步 Stream。

## Config And Data

- 配置集中在 `application.yml`、`.env` 和 `@ConfigurationProperties` 类中。
- API Key、数据库密码等敏感信息只放 `.env`，不得提交到 Git。
- 不要在 Service 中散落 `@Value`。
- 本地默认后端端口是 `8080`：`server.port: ${SERVER_PORT:8080}`。
- 开发环境 `ddl-auto` 可为 `update`，生产环境不能依赖自动建表。

## Frontend Rules

- API 调用集中在 `frontend/src/api/`，复用 `request.ts` 的 Axios 实例。
- 类型定义放在 `frontend/src/types/`，不要在页面里重复定义共享接口。
- 页面放在 `frontend/src/pages/`，可复用 UI 放在 `frontend/src/components/`。
- 路由常量放在 `frontend/src/constants/routes.ts`。
- 组件交互优先使用现有设计语言和 `lucide-react` 图标。

## Testing

- 后端测试使用 JUnit 5 + Mockito + AssertJ。
- 测试意图用中文 `@DisplayName` 描述，复杂场景用 `@Nested` 分组。
- 集成测试使用 H2 配置；限流相关测试需要真实 Redis。
- 改后端公共能力时至少运行 `./gradlew :app:test --no-daemon`。
- 改前端时至少运行 `cd frontend && pnpm run build`。

## Never Do

- 不要 `throw new RuntimeException(...)`，业务失败必须用 `BusinessException`。
- 不要直接返回 Entity 给前端。
- 不要把 `@Value` 散落在 Service 中。
- 不要在事务内调用 LLM、S3 或外部 HTTP。
- 不要同类内部调用 `@Transactional` 方法。
- 不要 `catch (Exception e) {}` 静默忽略。
- 不要循环调用 DB，优先批量查询或批量写入。
- 不要硬编码密钥、Token、数据库密码。
- 不要使用 `Executors.newXxxThreadPool()`，需要线程池时显式配置 `ThreadPoolExecutor`。

## More Rules

- 后端 Java 细则：`.claude/rules/backend.md`
- AI、限流、异步细则：`.claude/rules/ai-and-async.md`
- 自适应面试 Agent 细则：`.claude/rules/interview-agent.md`
- 前端细则：`.claude/rules/frontend.md`
