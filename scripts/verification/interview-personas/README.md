# Java 后端模拟候选人会话测试

五份角色脚本共用 `run_interviews.py`，通过真实 HTTP API 完成面试并读取正式报告。运行会创建测试账号下的会话、提交合成回答、调用面试模型及候选人模型；请使用专用测试账号和测试环境。

本工具不自行评估候选人，不预设最终等级，不把角色标签交给面试官。单元测试中的本地 HTTP 固定响应只验证客户端协议，没有可用于正式运行的假模型模式。

## 测试材料与五类角色

所有角色使用同一份 [简历](java-backend-resume.md) 和 [JD](java-backend-jd.md)，以 `EVALUATION`、`EXPERIENCED` 创建会话。

| 参数 | 角色脚本 | 行为设定 | 人工复核重点 |
| --- | --- | --- | --- |
| `novice` | [完全不懂领域](personas/novice.md) | 不懂技术和项目；不会时明确表达 | 是否持续诱导作答，或将复述提示当成掌握 |
| `jargon` | [术语熟练但不懂项目](personas/jargon.md) | 能复述术语，无法解释实现与个人贡献 | 是否被术语流畅度误导，是否追问实际机制 |
| `concise` | [简短且正确](personas/concise.md) | 少说话，保留正确结论的必要前提 | 是否把短回答误判为不会，是否重复追问已答内容 |
| `detailed` | [详细且正确](personas/detailed.md) | 详细解释机制、异常和取舍 | 是否合理切换已充分验证的主题 |
| `inarticulate` | [理解正确但表达较弱](personas/inarticulate.md) | 组织较乱、用词不熟练，但理解正确 | 是否通过澄清获取证据，是否因语言形式扣分 |

后三类共享 [虚构项目事实册](project-facts.md)，技术理解设定相同，只改变表达方式。前两类不接收事实册。事实册不发送给面试官，也不是审题标准。角色扮演仍可能失真，不能仅凭预设名称断言模拟答案实际正确或属于某等级。

Planner 自主决定本场模块、重点和题目，各会话不保证相同。相同模块名也不表示题目难度和证据量相同；输出不作自动排名，不断言五类角色必须按某顺序得分。

## 环境配置

需要 Python 3.8+。后端需启用 adaptive-agent，测试账号已存在，且已配置默认文本 Provider。

首次安装数据库读取和解密依赖（建议使用虚拟环境）：

```bash
python3 -m venv /tmp/interview-personas-venv
/tmp/interview-personas-venv/bin/pip install -r scripts/verification/interview-personas/requirements.txt
```

在 `scripts/verification/interview-personas/.env` 填写配置，通过 `--env-file` 指定。文件已被 Git 忽略，不应提交真实配置。无 `--env-file` 时读取仓库根目录 `.env`；`--no-env-file` 禁止文件加载。终端中已设置的环境变量优先。文件采用字面量 `KEY=VALUE` 格式，可加引号，不进行变量展开或执行命令。

| 变量 | 内容 |
| --- | --- |
| `INTERVIEW_API_BASE_URL` | 应用根地址，默认 `http://localhost:8080`，不包含 `/api` |
| `INTERVIEW_EMAIL`、`INTERVIEW_PASSWORD` | 已有测试账号的登录信息；未配置时在交互终端输入，密码不回显 |
| `INTERVIEW_API_TOKEN` | 可选，已有 accessToken 时优先使用，不含 `Bearer `；无效时明确失败 |
| `POSTGRES_HOST`、`POSTGRES_PORT`、`POSTGRES_DB`、`POSTGRES_USER`、`POSTGRES_PASSWORD` | 与服务一致的数据库连接；默认值与 application.yml 相同 |
| `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` | 可选，覆盖相应 PostgreSQL 配置，URL 使用 `jdbc:postgresql://...`，仅支持 libpq 兼容查询参数，不支持 currentSchema 等 JDBC 专用参数 |
| `APP_AI_CONFIG_ENCRYPTION_KEY` | 与后端一致的 Provider 加密密钥，用于内存解密数据库 API key |
| `INTERVIEW_PROVIDER_ID` | 可选，覆盖账号默认文本 Provider，也可用 `--provider` |

脚本通过正常登录获取 token，并通过 `/api/auth/me` 获取当前身份，只读查询该账号的默认文本 Provider 和启用的 Provider 配置。没有默认项时明确报错，不猜测账号或自动选择其他 Provider。脚本不创建账号、修改数据库或服务配置。

候选人模型默认复用面试 Provider；可通过 `--simulator-provider ID` 指定同账号的另一个 Provider。自动读取地址、加密 API key、模型、temperature 和 thinking_disabled。URL 版本路径、空温度默认值 0.2 及禁用思考请求结构沿用后端规则。数据库没有 max_tokens 字段，仍使用 `--max-tokens`，默认 8192。数据库连接在模型调用前关闭，凭据不写入日志或报告。

若后端显式使用开发加密密钥，需同时保持 `APP_AI_CONFIG_REQUIRE_ENCRYPTION_KEY=false` 和 `APP_AI_CONFIG_ALLOW_FALLBACK_ENCRYPTION_KEY=true`；脚本不会自行启用这一配置。

兼容原有显式候选人配置：同时提供 `SIMULATOR_BASE_URL`、`SIMULATOR_API_KEY`、`SIMULATOR_MODEL`。只提供部分会报错，不能与 `--simulator-provider` 混用。再提供 `INTERVIEW_PROVIDER_ID` 时无需访问数据库。显式 BASE_URL 保持原样，不自动补 `/v1`。

候选人模型通过 `/chat/completions` 调用，需支持 `response_format: {"type":"json_object"}` 和 `max_tokens`。不支持时明确失败，不切换协议。面试与候选人上下文相互独立。

## 运行

在仓库根目录执行。先填写上述 `.env`。以下使用已安装依赖的虚拟环境。

```bash
# 只列出角色，不联网
python3 -B scripts/verification/interview-personas/run_interviews.py --list

# 串行运行全部五类角色；为每类创建一个独立会话
/tmp/interview-personas-venv/bin/python -B scripts/verification/interview-personas/run_interviews.py \
  --env-file scripts/verification/interview-personas/.env \
  --persona all --output /tmp/java-interview-personas

# 单独运行一类；其他选择为 novice、jargon、detailed、inarticulate
/tmp/interview-personas-venv/bin/python -B scripts/verification/interview-personas/run_interviews.py \
  --env-file scripts/verification/interview-personas/.env \
  --persona concise --output /tmp/java-interview-personas
```

每次执行创建新的时间戳目录，不覆盖旧结果。会话沿用后端的轮次预算和结束机制，脚本不增加面试轮次上限、不强制提前结束，也不把达到轮次预算当作评估成功。

模拟候选人只能看到自己的角色设定、公共简历、允许的项目事实，以及本场公开题面和自己的实际历史回答。即使 API 返回评分、反馈或额外字段，也不会把它们传给候选人模型。代码材料按字段白名单投影，不传 `reviewGuide`。TEXT 追问可读取关联代码及历史提交，但不能以该轮名义修改代码；CODE_REPAIR 使用真实 `codeRepair.code` 协议提交完整代码。

## 最终评分输出

每个已完成会话读取 `GET /api/adaptive-agent-interviews/{sessionId}/report`，输出报告中的全部模块：

- `order`、`dimension`、`focus`：实际模块和考察重点。
- `depthLevel`：L0～L4，沿用系统量规。正式报告使用该模块最近一次正式评估，不是最高等级或平均值。
- `confidence`：原始置信度，不是百分制成绩，也不用于换算分数。
- `rationale`：正式判定依据，包含报告提供的证据不足说明。
- 原始 `report.json` 保留报告中的证据、薄弱点及其他公开字段。

未考察模块保留 `depthLevel=null`、`confidence=null`；表格显示“未考察”。不存在 0 分填充、缺失模块跳过或脚本自评分。报告模块与计划不匹配时明确失败。

每个角色目录包含：

| 文件 | 内容 |
| --- | --- |
| `creation-request.json` | 实际创建参数与公共简历、JD |
| `simulator-system.txt` | 本次候选人角色、事实及回答协议快照 |
| `session.json` | 最近一次取得的正式会话快照，含完整公开问答 |
| `turn-NNN-answer.json` | 提交前保存的原始回答请求，便于核对不确定结果 |
| `turn-NNN-session.json` | 每次成功提交后的会话快照 |
| `turn-NNN-simulator.json` | 候选人模型可见输出、实际模型、结束原因和 usage；不保存专有推理字段 |
| `api-requests.jsonl`、`simulator-requests.jsonl` | 请求路径、结果与耗时，不保存认证头 |
| `report.json` | API 返回的正式报告 |
| `scores.json`、`scores.csv`、`scores.md` | 全部模块最终评分，CSV 可用表格软件打开 |
| `status.json` | 执行阶段、会话 ID、角色、完成/失败/中断状态和候选人提示摘要 |

运行目录根部也有合并后的 `scores.*` 和 `summary.json`。**合并评分表仅包含成功收集完整报告的会话，必须同时查看 summary 的 completed、failed、notStarted 与 interrupted。** 零行评分表不表示所有候选人得分为零。`humanReview=PENDING` 表示尚未人工确认角色表现和面试质量。

各会话快照记录应用报告的面试模型与 runtimeVersion；脚本不假定本地提示词就是部署中的版本。比较提示版本时，应单独记录实际部署版本，固定候选人模型和材料并重复采样。

## 超时、失败和补取报告

默认面试 API 的单次 socket 等待超时为 180 秒，候选人模型为 120 秒，均可通过 `--api-timeout`、`--model-timeout` 调整。`--max-tokens` 默认 8192，限制候选人模型输出；若输出被截断则明确失败，不提交残缺代码。

HTTP 错误、业务错误、结构非法、未知题型或未完成会话均保存失败状态，不隐式重试、不修复模型输出。答题状态为 PROCESSING、RETRYABLE 或已接受回答时，不重新生成另一份答案。创建超时可能没有返回 sessionId；应通过测试账号的面试历史核对远端状态，不能盲目重建会话。

Ctrl+C 中断时保留进度，远端已收到的请求可能仍在执行。已知 sessionId 且远端后来完成时，可以只补取报告；这个命令不提交任何回答，只需应用地址和账号登录信息或 `INTERVIEW_API_TOKEN`，不读取模型配置或数据库：

```bash
/tmp/interview-personas-venv/bin/python -B scripts/verification/interview-personas/run_interviews.py \
  --env-file scripts/verification/interview-personas/.env \
  --collect '实际-session-id' --output /tmp/java-interview-personas
```

补取结果的角色标记为 `collected`，不猜测原角色。尚未完成的会话明确报错，没有临时评分替代。工具不实现自动续答或重试失败的后台工作。

退出码：0 表示所选会话均成功收集完整报告；1 表示至少一项失败；2 表示配置错误；130 表示用户中断。完成不代表语义质量通过。

## 本地验证

```bash
timeout 60s /tmp/interview-personas-venv/bin/python -B -m unittest discover \
  -s scripts/verification/interview-personas -p 'test_*.py' -v
```

测试使用临时本地 HTTP 服务，需要允许绑定 `127.0.0.1` 端口。覆盖五类角色的请求路由、公共数据隔离、代码提交、失败不重试、报告完整性及未考察语义，不调用真实模型或应用数据库。这些测试通过不能证明角色扮演准确、面试策略有效或真实评分合理。
