# Task Specification

## Task Shape

- **Shape**: `single-full`

## Goals

- 首题 ReAct 在模型一次返回多个工具时只执行第一个工具，并在获得首个有效工具结果后生成最终 ASK。
- 消除重复调用首个工具导致的模型步预算耗尽。

## Non-Goals

- 不提高模型步数或工具调用预算。
- 不改变重复调用去重和 ToolGateway 白名单。
- 不恢复当前已下线的 question_bank_search。

## Constraints

- Spring Boot 4.1.0、Java 21、Spring AI 2.0.0。
- 保留当前“多工具响应取第一个”的行为。
- 后端测试必须在 60 秒内结束。
- 只修改当前问题所需代码，保留工作区既有改动。

## Environment

- **Project root**: `/home/noshiro/interview-guide-agent`
- **Language/runtime**: Java 21 / Spring Boot 4.1.0
- **Package manager**: Gradle Wrapper
- **Test framework**: JUnit 5 / Mockito / AssertJ
- **Build command**: `./gradlew :app:test --no-daemon`

## Risk Assessment

- [x] External dependencies — 定向单元测试不依赖真实 Provider。
- [x] Breaking changes — 仅在本轮已有有效工具 observation 后停止继续注册工具。
- [x] Large file generation — 无。
- [x] Long-running tests — 使用 60 秒 timeout。

## Deliverables

- 模型上下文能够判定本轮是否已有成功工具结果。
- interviewer 后续请求不再携带工具 callbacks。
- 单元测试覆盖首次允许工具、工具成功后禁用工具。

## Done-When

- [x] 多工具响应仍只映射第一个工具。
- [x] 首个工具成功后的下一模型请求注册空工具列表。
- [x] 相关网关、映射器、运行时测试通过。

## Final Validation Command

```bash
timeout 60s ./gradlew :app:test --no-daemon --console=plain --tests '*AdaptiveAgentResponseMapperTest' --tests '*SpringAiAdaptiveAgentModelGatewayTest' --tests '*BoundedReActRuntimeTest'
```
