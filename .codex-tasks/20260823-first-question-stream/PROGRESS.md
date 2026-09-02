# Progress Log

## Session Start

- **Date**: 2026-08-23 18:25 +08:00
- **Task name**: `20260823-first-question-stream`
- **Task dir**: `.codex-tasks/20260823-first-question-stream/`
- **Spec**: See SPEC.md
- **Plan**: See TODO.csv (4 milestones)
- **Environment**: Java 21 / Spring Boot 4.1.0 / React / Gradle / pnpm

## Context Recovery Block

- **Current milestone**: #4 — 执行跨端回归验证
- **Current status**: DONE
- **Last completed**: #4 — 执行跨端回归验证
- **Current artifact**: `TODO.csv`
- **Key context**: 同步 POST 只返回 CREATED 骨架，前端每 2 秒 GET；后端首题后台生成不传 deltaSink，且 ReAct 仅第一模型步流式。
- **Known issues**: 工作区存在大量既有未提交重构，所有修改必须保持行级最小化。
- **Next action**: 无，全部里程碑已完成。

## Milestone 1: 还原首题显示与轮询链路

- **Status**: DONE
- **Started**: 18:20
- **Completed**: 18:25
- **What was done**:
  - 核对创建 Controller、ApplicationService、ReAct runtime、前端 API 和页面状态。
  - 确认首题仅在 completeCreation 后通过 GET 整体出现。
- **Key decisions**:
  - Decision: 新增创建 SSE，保留同步创建接口；复用现有 SSE 解析器和首题 JSON content 增量提取。
  - Reasoning: 最小化协议与 UI 改动，同时兼容既有非浏览器调用方。
- **Problems encountered**:
  - Problem: ReAct 第一步若为工具调用，最终 ASK 位于非流式步骤。
  - Resolution: 有 deltaSink 时每个 ReAct 模型步骤都使用流式网关。
  - Retry count: 0
- **Validation**: `rg -n "CREATED|loadSession|generateFirstTurn|completeCreation" ...` → exit 0
- **Files changed**:
  - `.codex-tasks/20260823-first-question-stream/*` — 任务真相文件。
- **Next step**: Milestone 2 — 实现后端首题创建 SSE

## Milestone 2: 实现后端首题创建 SSE

- **Status**: DONE
- **Started**: 18:25
- **Completed**: 18:30
- **What was done**:
  - 新增候选人创建命令、创建事件 sink 和 SSE 事件发送器。
  - 新增 `POST /api/adaptive-agent-interviews/stream`，发送 created/delta/done/error。
  - 首题 generateFirstTurn 接收 deltaSink；ReAct 工具后的后续模型步骤继续流式。
  - 增加应用服务、Controller 和运行时测试。
- **Key decisions**:
  - Decision: 保留同步 POST，新增 SSE POST；创建任务仍由现有 creationExecutor 执行。
  - Reasoning: 不破坏 MCP/同步调用方，并确保浏览器连接断开不取消后台落库。
- **Problems encountered**:
  - Problem: 原 ReAct 仅第一模型步骤流式，工具后的 ASK 无增量。
  - Resolution: deltaSink 非空时每一步均调用 nextActionStreaming。
  - Retry count: 0
- **Validation**: `timeout 60s ./gradlew ... --tests '*AdaptiveInterviewApplicationServiceTest' --tests '*AdaptiveInterviewControllerTest' --tests '*BoundedReActRuntimeTest'` → exit 0, BUILD SUCCESSFUL in 15s
- **Files changed**:
  - `CandidateInterviewCreationCommand.java`
  - `InterviewCreationEventSink.java`
  - `SseEventSender.java`
  - `SseInterviewCreationEventSink.java`
  - `AdaptiveInterviewApplicationService.java`
  - `AdaptiveInterviewController.java`
  - `BoundedReActRuntime.java`
  - 对应测试文件
- **Next step**: Milestone 3 — 前端改用创建流并渐进显示首题

## Milestone 3: 前端改用创建流并渐进显示首题

- **Status**: DONE
- **Started**: 18:30
- **Completed**: 18:33
- **What was done**:
  - API 层新增 createStream，并抽取创建/答题共用 SSE 消费逻辑。
  - 页面收到 created 后立即导航，收到 delta 后增量提取 content 并渲染。
  - 活动创建流期间禁止 CREATED 轮询；刷新恢复场景保留原轮询。
  - 抽取并测试 partial content 解析器，首字符到达即可返回。
- **Key decisions**:
  - Decision: 流中保留原始结构化 JSON，前端继续容错提取 content。
  - Reasoning: 与下一题现有协议一致，不额外设计文本分片协议。
- **Problems encountered**:
  - Problem: 页面导航后的 sessionId effect 可能与活动流同时 GET。
  - Resolution: 用 creationStreamActive ref 明确区分活动流和刷新恢复。
  - Retry count: 0
- **Validation**: `node --test src/pages/adaptiveInterviewStream.test.ts` → 2 tests pass；`pnpm run build` → exit 0
- **Files changed**:
  - `frontend/src/api/adaptiveInterview.ts`
  - `frontend/src/pages/AdaptiveInterviewPage.tsx`
  - `frontend/src/pages/adaptiveInterviewStream.ts`
  - `frontend/src/pages/adaptiveInterviewStream.test.ts`
- **Next step**: Milestone 4 — 执行跨端回归验证
## 2026-08-23 结构化重试流式边界

- 使用 Spring AI `MessageAggregator` 聚合流式工具调用，移除二次非流式模型请求。
- 结构化重试继续使用流式调用，前端提取最新一次 `content`。
- 首次构建因 ES target 不支持 `Array.at` 失败，已改用普通下标后重试。

## Milestone 4: 执行跨端回归验证

- **Status**: DONE
- **Completed**: 18:42
- **What was done**:
  - 确认 Spring AI 流式工具调用在同一请求内聚合，不再触发非流式二次请求。
  - 确认结构化重试仍流式输出，前端读取最新一次 `content`。
  - 完成后端四组定向测试、前端增量测试、生产构建和差异检查。
- **Validation**:
  - 后端定向测试：`BUILD SUCCESSFUL in 20s`。
  - 前端增量测试：通过。
  - `pnpm run build`：通过，仅有既有 CSS 与 Browserslist 警告。
- **Retry count**: 1
