# Progress Log

## Context Recovery Block

- **Current milestone**: #7 — T03 验收与提交
- **Current status**: DONE
- **Last completed**: T03 / commit `42ea980`
- **Current artifact**: `docs/design_spec/35-memory-three-layer-tickets.md` T03
- **Key context**: ASK/CALL_TOOL 已统一为 `PLANNED → EXECUTING → SUCCEEDED → APPLIED`，FAILED 只能通过显式 API 产生新 Intent 重试。
- **Known issues**: 无 T03 未闭环问题；后端全量测试依然留到 T08 在 60 秒门禁下统一处理。
- **Next action**: 进入 T04/T05，将 answered turn 固化为不可变 Episode，再实现题目曝光和召回去重。

## Delivery

- 外部动作前：`ActionIntentTransactionService` 原子保存 Intent 和 Pending Patch。
- 外部动作后：问题/ToolExecution 与 `SUCCEEDED` 同事务落库，再单独应用 ActionResult Patch。
- ASK 在 final turn 落库后才向 SSE 发布；CALL_TOOL 在 Intent 之前只做无副作用参数提案与校验。
- 恢复扫描 PLANNED、超时 EXECUTING 和 SUCCEEDED；工具重用 Intent idempotency key。
- 删除 `BoundedReActRuntime`、`ReActBudget`、`AgentToolExecutor`、首题直写和 PATCH 随机 invocation 路径。

## Validation

- ActionIntent/ASK/CALL_TOOL/幂等工具定向测试：通过。
- application/core/runtime/tool/intent/working 模块回归：60 秒内通过。
- `AdaptivePackageIsolationTest`、Controller 和 Spring AI gateway 回归：通过。
- `git diff --cached --check`：通过。
