# Task Specification

## Task Shape

- **Shape**: `single-full`

## Goals

- 为候选人 Provider 增加显式 thinking 开关，并让自适应 Agent 请求按 Provider 配置关闭 thinking。
- 结构化输出失败后只重试一次，避免 Spring AI advisor 与业务层重复重试。
- 保持现有输出 token 和 deadline 配置不变。

## Non-Goals

- 不改异常分类与传播策略。
- 不改输出 token 上限或任何 deadline。
- 不新增静默 fallback。

## Constraints

- 遵循现有 Candidate Provider 配置、Spring AI `OpenAiChatOptions.extraBody` 和统一 `StructuredOutputInvoker`。
- 保留用户当前工作区已有改动，只修改本任务必要位置。
- Java 21、Spring Boot 4.1.0、Spring AI 2.0.0；后端测试限时 60 秒。

## Environment

- **Project root**: `/home/noshiro/interview-guide-agent`
- **Language/runtime**: Java 21 / Spring Boot 4.1.0
- **Package manager**: Gradle 8.14
- **Test framework**: JUnit 5 / Mockito / AssertJ
- **Build command**: `./gradlew :app:compileJava`
- **Existing test count**: 未统计，本任务运行定向测试

## Risk Assessment

- [x] External dependencies — 实际 DeepSeek Provider 已验证支持 `thinking.type=disabled`
- [x] Breaking changes — Provider DTO/持久化/API 请求字段需同步
- [x] Large file generation — 不涉及
- [x] Long-running tests — 使用 `timeout 60s`

## Deliverables

- Provider thinking 配置贯穿请求 DTO、响应 DTO、实体和自适应模型 options。
- 结构化输出总尝试次数固定为 2（首次 + 1 次重试），且禁用 advisor 内部重试叠加。
- 单元测试和 HTTP 契约测试覆盖请求体与重试次数。

## Done-When

- [ ] Provider 可显式配置 thinking=false，DeepSeek 请求发送 `thinking.type=disabled`。
- [ ] 单次结构化调用最多执行 2 次真实模型请求。
- [ ] 定向测试及后端编译在 60 秒约束内通过。

## Final Validation Command

```bash
timeout 60s ./gradlew :app:test --no-daemon --console=plain --tests '*CandidateLlmProviderServiceTest' --tests '*AdaptiveModelOptionsFactoryTest' --tests '*AdaptiveModelOptionsHttpContractTest' --tests '*StructuredOutputInvokerTest' --tests '*SpringAiPlanningAgentTest'
```
