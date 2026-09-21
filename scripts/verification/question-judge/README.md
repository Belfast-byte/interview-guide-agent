# 文本题质量离线测评

实现规格见 [Spec 46](../../../docs/design_spec/46-question-quality-llm-judge-evaluation-spec.md)。入口是显式启用的 `QuestionJudgeOfflineTest`，不启动生产 App、HTTP、队列、调度或 Flyway。默认常规测试跳过真实调用。数据库连接设为只读，模型调用在读取事务结束后执行。

首版仅 TEXT；首题、后续题和追问共用六维量规。缺失历史验证事实时允许报告证据不足；当前 reader 不将后来更新的 Assessment 当成生成时事实。`questionReason` 当前不是候选人公开字段，故不导出；代码关联材料只使用 `publicView()`。题型为 CODE_REPAIR 时跳过。

## 准备

需要能连接验收 PostgreSQL、解密已保存 Provider 的 `APP_AI_CONFIG_ENCRYPTION_KEY`，以及明确授权的测试账号、该账号的 Provider ID。沿用 `application.yml` 的数据库环境配置和现有 Provider 注册器；不要在命令、报告或聊天中粘贴 API Key。独立进程读取环境，Gradle test 不自动加载 `.env`，应由本地安全启动方式注入所需环境。

生成可分享的合成校准材料（生成后的文件不提交 Git）：

```bash
python3 scripts/verification/question-judge/generate_fixtures.py /tmp/question-judge-fixtures
```

样例覆盖不同岗位、正常开放题、错误前提、条件缺失、唯一解要求、聚焦、代答、正常追问和重复。`proposed-labels.json` 是待人工确认的参考意见，不能当作已有人类标注的真值。重复运行一致性检查使用同一 fixture、模型及提示版本，保留所有 attempt。

## 运行

配置以下非密钥参数，例如（替换账号和 Provider 标识）：

```bash
export QUESTION_JUDGE_LIVE=true
export QUESTION_JUDGE_EMAIL='your-test-account@example.com'
export QUESTION_JUDGE_PROVIDER='saved-provider-id'
export QUESTION_JUDGE_REVISION="$(git rev-parse HEAD)"
export QUESTION_JUDGE_MAX_SAMPLES=8
export QUESTION_JUDGE_TIMEOUT_SECONDS=45
export QUESTION_JUDGE_MAX_INPUT_TOKENS=12000
export QUESTION_JUDGE_MAX_OUTPUT_TOKENS=4000
export QUESTION_JUDGE_OUTPUT=/tmp/question-judge-results
export QUESTION_JUDGE_FIXTURES=/tmp/question-judge-fixtures/fixtures.json
./gradlew :app:test --tests '*QuestionJudgeOfflineTest' --rerun-tasks --no-daemon
```

`--rerun-tasks` 防止环境参数变更后 Gradle 复用之前的跳过结果。一次运行串行执行，最多 30 个已选样例且配置 timeout × maxSamples 不超过 480 秒，测试总超时 600 秒；生产答题预算不受影响。若样例超过 deadline，后续样例标为 PREVIOUS_CALL_TIMEOUT 并停止派发，防止迟到请求与下一请求重叠。每样例最多一次实际 Provider 请求（SDK 重试关闭，显式使用 Provider JSON object 输出模式；不支持时明确失败），输入超限不截断为伪完整题面，记录 SKIPPED。

读取已发布个人面试快照时，取消 `QUESTION_JUDGE_FIXTURES`，改用：

```bash
unset QUESTION_JUDGE_FIXTURES
export QUESTION_JUDGE_SESSION='authorized-session-id'
export QUESTION_JUDGE_TURNS='1,2,3'
```

入口先定位测试账号，再核验 Provider 与会话归属。该环境变量入口仅用于受信任本地测评，不能暴露为以 email 代替认证的 HTTP API。底层 reader 支持显式 tenant + owner 核验；当前命令入口仅接受个人会话。

## 报告与复核

输出目录权限 0700、文件 0600，每次运行独立目录；每完成一个样例立即保存观察和进度，失败不会丢弃先前结果。报告含不可变材料、材料摘要、提示摘要、模型/量规/代码版本、attempt、六维结论、精确引用位置、实际返回 usage。没有 usage 时标未知；`requestsAttempted` 是应用层派发次数，不冒称远端一定收到或计费。Linux/POSIX 是当前离线入口支持环境。

`summary.json` 包含完整执行状态分母、首题/后续及 fixture/已发布分组、问题分布、已知 token 消耗和 usage 未知次数。FAILED、SKIPPED 和 INSUFFICIENT_EVIDENCE 不归为“无问题”。测试进程成功只表示完成测评报告生成；必须查看报告中的失败数，不能据此宣布所有 Judge 调用成功。

对照参考标签逐条复核：引用是否真实、问题是否成立、是否误伤开放题/合理追问、是否漏掉已知问题。将人工结论与理由作为额外文件保存在该运行目录，不覆盖模型报告；误报/漏报统计只针对已人工确认的样例。首版不输出自动总分、模型排名或候选人评分，也不把观察送回业务 Agent。未完成人工标注/复核时保持 `humanReview=PENDING`。
