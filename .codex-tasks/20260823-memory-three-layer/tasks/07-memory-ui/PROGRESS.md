# Progress Log

## Context Recovery Block

- **Current milestone**: DONE — T21-T22 候选人记忆 API 与 UI
- **Current status**: DONE
- **Last completed**: #3 — T07 契约回归
- **Current artifact**: `CandidateMemoryControllerTest.java` / `candidateMemoryView.test.ts`
- **Key context**: API 与 TypeScript 契约已逐字段一致；Episode 只公开链路、Topic、Depth、状态和 createdAt。
- **Known issues**: 前端构建存在既有 Tailwind CSS minify warning，不影响构建成功。
- **Next action**: 进入 T09 三层记忆端到端门禁。

## 2026-08-24 21:28

- 首次组合门禁暴露 EpisodeResponse 白名单漏断言 `triggerType`，以及组件测试文件为空。
- 同步字段契约并补齐 4 个真实 Vitest 组件测试，覆盖跨页祖先链、触发来源和补全状态。
- 后端 `CandidateMemoryControllerTest`、前端组件测试与生产构建全部通过。
