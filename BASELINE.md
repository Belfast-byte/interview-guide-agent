# Agent 面试 MVP 基线

> 记录时间：2026-07-26（Asia/Shanghai）
>
> 目标仓库：`Belfast-byte/interview-guide-agent`
>
> 基线状态：设计完成，功能待实施

## 1. 基线来源

- 上游仓库：`Snailclimb/interview-guide`
- 上游基准提交：`646b23ec64a96b04fdc50bebffd412a49f77afb3`
- 上游提交说明：`docs: 更新知识库题库与面试说明`
- 上游提交时间：`2026-07-24T13:35:12+08:00`

本仓库保留完整上游历史，便于追溯来源和选择性同步更新。Git 远程约束如下：

- `origin`：`Belfast-byte/interview-guide-agent`，本项目唯一推送目标；
- `upstream`：`Snailclimb/interview-guide`，仅用于拉取，上游 push URL 已禁用。

## 2. 本次基线新增内容

- Agent 文本技术面试 MVP 技术设计；
- Agent 文本技术面试 MVP 实施计划；
- 项目领域术语上下文；
- 工程导师协作规则与 `mentor-engineering` Skill；
- Agent Skill 所需的 `.gitignore` 规则。

本基线尚未实现 Agent 面试业务代码。

## 3. MVP 范围

最小闭环包括：

1. 输入 Java 后端岗位 JD 和候选人简历；
2. 生成首题并进行最多六轮动态文本技术面试；
3. 每轮根据回答选择追问、切换考察目标或结束；
4. 输出能够定位到原始回答片段的能力证据报告；
5. 保持现有标准文本面试流程兼容。

明确不包括语音、多 Agent、外部检索、在线编程、百分制评分和结束后再次调用 LLM 生成结论。

## 4. 验证结果

### 后端

执行：

```powershell
.\gradlew.bat :app:test --no-daemon
```

结果：

- 测试总数：253；
- 失败：0；
- 跳过：49；
- Gradle 报告耗时：1m48.79s；
- 测试报告：`app/build/reports/tests/test/index.html`。

49 个跳过测试意味着部分依赖外部环境或尚未恢复的测试没有进入本次验证，不能把当前结果理解为所有运行时集成都已覆盖。

### 前端

依赖按照 `frontend/pnpm-lock.yaml` 安装。由于当前 PowerShell 沙箱中的 pnpm 包装入口在运行脚本时出现 `fetch failed`，分别执行了与 `pnpm run build` 等价的两个构建步骤：

```powershell
.\node_modules\.bin\tsc.cmd
.\node_modules\.bin\vite.cmd build
```

结果：

- TypeScript 检查通过；
- Vite 生产构建通过；
- 转换 4118 个模块；
- Vite 构建耗时 8.86s。

构建存在以下非阻塞警告：

- 生成 CSS 中出现 3 条空 `:where()` 相关语法警告；
- `syntax-highlighter` chunk 约 697.69 kB，超过 500 kB 提示线；
- Browserslist 数据约 7 个月未更新。

## 5. 本地验证环境

- Java：OpenJDK 21；
- Node.js：v24.11.1；
- 前端锁定的 pnpm：10.26.2。

## 6. 下一步入口

从实施计划的确定性业务内核开始：

1. 固定 Agent 面试领域模型和接口契约；
2. 实现纯 Java 状态机；
3. 实现决策、证据校验和确定性报告规则；
4. 在不依赖数据库和真实 LLM 的条件下完成单元测试。
