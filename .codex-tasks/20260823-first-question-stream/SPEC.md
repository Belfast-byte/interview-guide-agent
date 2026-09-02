# Task Specification

## Task Shape

- **Shape**: `single-full`

## Goals

- 创建自适应面试时通过 SSE 立即返回 CREATED 会话，并流式推送首题决策增量。
- 首题文本的第一个已生成字符到达浏览器后立即显示，不再依赖轮询完成后整题刷新。
- 首题前若发生工具调用，最终 ASK 所在的后续 ReAct 步骤仍保持流式。

## Non-Goals

- 不改变规划器、模型提示词、工具预算或面试状态机。
- 不改变现有同步创建接口，保留非浏览器调用方兼容性。
- 不引入 WebSocket 或新的前端状态库。

## Constraints

- Spring Boot 4.1.0、Java 21、Spring AI 2.0.0、React。
- SSE 任务继续在已有显式创建线程池执行，LLM 不进入事务。
- 失败必须落 FAILED 并通过 SSE error 事件显式返回。
- 后端测试使用 60 秒硬超时；前端至少通过 `pnpm run build`。
- 保留工作区现有未提交改动，只修改首题流式链路所需位置。

## Environment

- **Project root**: `/home/noshiro/interview-guide-agent`
- **Language/runtime**: Java 21 / TypeScript / React
- **Package manager**: Gradle Wrapper / pnpm
- **Test framework**: JUnit 5 / Mockito / TypeScript build
- **Build command**: `./gradlew :app:test --no-daemon`; `pnpm run build`

## Risk Assessment

- [x] External dependencies — 单元与构建验证不依赖真实 Provider。
- [x] Breaking changes — 新增 `/stream`，现有创建接口保留。
- [x] Large file generation — 无。
- [x] Long-running tests — 后端命令使用 60 秒 timeout。

## Deliverables

- 创建过程事件 sink 与 SSE 创建端点。
- 创建应用服务把最终 ASK 的 deltaSink 传入 ReAct。
- ReAct 每个模型步骤在有 deltaSink 时均走流式网关。
- 前端 createStream API 与首题渐进显示状态。
- 后端定向测试与前端生产构建。

## Done-When

- [ ] 创建 SSE 依次支持 created、delta、done/error 事件。
- [ ] 工具调用后的最终 ASK 仍从第一个文本增量开始推送。
- [ ] 前端活动创建流不启动首题轮询，收到 delta 即显示首题。
- [ ] 后端定向测试和前端 build 全部通过。

## Final Validation Command

```bash
timeout 60s ./gradlew :app:test --no-daemon --console=plain --tests '*AdaptiveInterviewApplicationServiceTest' --tests '*AdaptiveInterviewControllerTest' --tests '*BoundedReActRuntimeTest' --tests '*SpringAiAdaptiveAgentModelGatewayTest'
cd frontend && pnpm run build
```

## Demo Flow

1. 在自适应面试创建页填写 JD、简历并选择 Provider。
2. 点击开始后立即进入 CREATED 会话视图。
3. 规划完成、interviewer 开始输出后，首题逐字出现。
4. done 事件到达后切换为 IN_PROGRESS 并启用回答栏。
