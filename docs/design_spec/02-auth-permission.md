# 认证与权限边界设计（Auth & Permission）

> 维护：Agent；上游设计决策以 `docs/design/` 为准。
>
> 状态：A0、A1、A2 已实施；候选人归属已覆盖自适应面试、传统面试、Agent Loop、简历、语音面试和日程
> 关联：[01-platform-design.md](../design/01-platform-design.md)（企业视角、两种报告视图、租户隔离的长期目标）

## 0. 实施前背景与问题

实施前系统没有任何登录环节，身份标识（`candidateId`）是**前端表单里的手填字符串**（`AdaptiveInterviewPage.tsx` 候选人标识输入框），随请求体直接传给后端。后果：

1. **身份无生产者**：`candidateId` 没有来源系统，谁都能写任意值。
2. **接口可冒名**：`/api/adaptive-agent-interviews/candidates/{candidateId}/ability-profile` 等接口按路径参数取数，任何人可以读取、甚至污染任意候选人的记忆、能力画像与评估报告。
3. **权限边界为零**：`/api/llm-provider/**`（模型与密钥配置）与候选人面试接口对所有人同等开放。

本设计解决两件事：**登录系统**（身份从哪里来）与**权限边界**（每个角色能碰哪些数据和接口）。

## 1. 第一原则：最小实现，不造轮子

本文档所有选型遵守三条纪律，与 `AGENTS.md` 的反过度工程规则一致：

1. **能抄不造**。Spring Security + JWT 是已被验证过无数遍的标准方案，实施时直接参考第 7 节列出的开源实现，按本项目结构裁剪即可，**不自行设计** token 格式、加密流程、刷新机制。
2. **没有需求就没有功能**。本期只有两个角色有真实业务入口（候选人、管理员），所以不实现 refresh token、token 黑名单、账号锁定、审计子系统、多租户 RBAC——全部为「将来可能需要」而做的设计一律砍掉。
3. **信任框架保证**。Spring Security 已解决的问题（密码哈希、Bearer 解析、URL 匹配）不再自写一层校验或兜底。

**明确不做清单**（及对应代价，可接受才砍）：

| 不做 | 理由 | 代价 |
|---|---|---|
| refresh token / token 旋转 | 单 access token（7 天）+ 过期重新登录，少一张表、少整套吊销逻辑 | 改密后旧 token 最长残留 7 天有效，MVP 阶段可接受 |
| token 黑名单 / 服务端登出 | 无状态 JWT 的意义就是不查库；登出 = 前端丢弃 token | 同上 |
| 账号连续失败锁定 | 复用现有 `@RateLimit` 对登录接口限流已足够 | 理论上可跨账号轮询爆破，限流 + BCrypt 成本已构成实质门槛 |
| 审计日志子系统 | 登录失败、越权拦截各打一条 WARN 日志即可 | 无结构化审计，出问题靠日志检索 |
| 多租户 / ENTERPRISE 角色实现 | 企业 Web 端不存在，实现了也是空转 | 二期再加，数据模型留 `tenant_id` 兜底 |

## 2. 角色模型

| 角色 | 说明 | 本期落地 |
|---|---|---|
| `CANDIDATE` | 候选人。注册即获得系统生成的 `candidateId`，拥有面试、简历、日程等个人数据 | 是 |
| `ENTERPRISE` | 企业用户（HR/面试官）。只能查看本租户内候选人的评估报告投影 | 否（仅数据模型预留 `tenant_id`） |
| `ADMIN` | 平台管理员。管理 LLM Provider、知识库等全局配置；**不默认拥有**候选人业务数据的读权限 | 是 |

角色用 `users.role` 一个字符串字段表达，**不建 role 表、不做 RBAC 权限点模型**——两个角色用枚举就够。

## 3. 认证方案

### 3.1 选型：Spring Security + JWT（无状态）

- 引入 `spring-boot-starter-security` 与 `jjwt`（io.jsonwebtoken）。
- 登录校验邮箱 + 密码（Spring Security 自带 `BCryptPasswordEncoder`），签发单个 **access token（7 天有效）**，sub 放 `userId`，claim 放 `role`。
- 后续请求经一个 `OncePerRequestFilter` 解析 `Authorization: Bearer` 头，将身份写入 `SecurityContext`。
- Controller/Service 通过 `@AuthenticationPrincipal` 取当前用户，**禁止从请求体、路径参数获取身份**。
- 登出 = 前端删除本地 token；改密 = 直接改库，旧 token 到期自然失效。

### 3.2 candidateId 的生产者

- 新增 `users` 表，注册时创建记录；主键（UUID）即 `candidateId`，注册时生成、终身不变。
- 前端登录后从 `/api/auth/me` 获取自己的 `candidateId` 用于展示，移除候选人标识输入框。
- `CreateAdaptiveInterviewRequest.candidateId` 字段删除，改由后端从 SecurityContext 注入。

### 3.3 认证接口（只有 3 个）

| 接口 | 说明 |
|---|---|
| `POST /api/auth/register` | 邮箱 + 密码注册，生成 candidateId；IP 级 `@RateLimit` |
| `POST /api/auth/login` | 登录，返回 access token；`@RateLimit` 限流，失败打 WARN 日志 |
| `GET /api/auth/me` | 当前用户信息（candidateId、role），前端启动时拉取 |

## 4. 权限边界

### 4.1 接口级（角色）

| 路径 | CANDIDATE | ADMIN | 匿名 |
|---|---|---|---|
| `/api/auth/register`、`/api/auth/login` | — | — | ✓ |
| `/api/adaptive-agent-interviews/**`（面试、作答、画像、报告） | ✓ 仅本人 | ✗ | ✗ |
| `/api/interview/skills/**` | ✓ 仅本人 | ✗ | ✗ |
| `/api/voice-interview/**` | ✓ 仅本人 | ✗ | ✗ |
| `/api/interview-schedule/**`、`/api/resumes/**` | ✓ 仅本人 | ✗ | ✗ |
| `/api/llm-provider/**`、知识库管理 | ✗ | ✓ | ✗ |
| `/internal/code-analysis/jobs/**` | ✗ | ✗ | ✗（仅内网/服务间 Header token） |

实现方式：URL 前缀与角色映射集中在**一个** `SecurityFilterChain` Bean 中声明（`requestMatchers(...).hasRole(...)`），不用 `@PreAuthorize` 散落各 Controller——权限矩阵只有一个事实源，审查时看一处即可。这正是 spring-security-samples 的标准写法。

### 4.2 数据级（归属）

接口级放行后，Service/Repository 层做**所有权校验**，这是权限边界的主体：

- 面试会话、画像、报告等按 ID 寻址的资源：Repository 查询必须带归属条件（`findByIdAndCandidateId`），查不到即抛业务异常——**归属校验融进查询本身**，不写「先 `findById` 再比对再抛 403」这种多一步的防御性代码。
- `GET /candidates/{candidateId}/ability-profile` **改为 `/me/ability-profile`**：路径里的身份参数是越权温床，一律消除，顺带省掉「路径 ID 与登录身份是否一致」的校验代码。
- 候选人业务表统一以 `candidate_id` 作为归属列：`resumes`、`interview_sessions`、
  `agent_interview_sessions`、`voice_interview_sessions`、`interview_schedule`，以及自适应面试相关表。
- `interview_sessions.candidate_id` 允许为空，仅用于 ADMIN 创建的知识库面试；普通候选人会话必须写入 owner。
- 语音 WebSocket 通过 `Sec-WebSocket-Protocol` 携带 Bearer JWT，握手阶段执行
  `findByIdAndCandidateId`，不能仅凭路径中的 sessionId 建连。

两层防线汇总：**URL 角色规则（Filter 层，一处配置）→ 归属条件查询（Repository 层，融进查询）**。没有第三层，企业租户范围等二期有真实角色了再加。

### 4.3 内部接口

`/internal/code-analysis/jobs/**` 不进入用户 JWT 认证体系，但统一要求
`X-Code-Analysis-Token` 服务间 Header 凭证。凭证必须通过
`APP_INTERVIEW_CODE_ANALYSIS_WORKER_TOKEN` 注入，缺失时 Worker Controller 不注册；部署层仍不得将该路径暴露公网。

## 5. 数据模型

新增 `users` 表，并在候选人业务表增加归属列：

```sql
users(
  id            varchar PK,       -- UUID，即 candidateId
  email         varchar unique not null,
  password_hash varchar not null, -- BCrypt
  role          varchar not null, -- CANDIDATE / ENTERPRISE / ADMIN
  tenant_id     varchar null,     -- 预留：二期企业租户
  created_at    timestamp
)
```

存量处理：现有 `candidateId` 为自由字符串的历史数据，开发环境直接清库重建；Flyway 只负责新增表、归属列和索引，**不做历史 owner 回填**。首个 ADMIN 账号用一条 SQL 手工插入，不做「启动引导创建」的配置机制。

文件哈希去重限定在候选人范围内，`resumes` 使用 `(candidate_id, file_hash)` 唯一索引，
避免相同文件跨账号命中并泄露历史分析结果。

## 6. 安全细则（只保留有实际威胁对应的）

- 密码：Spring Security `BCryptPasswordEncoder`，默认值即可。
- JWT 签名密钥放 `.env`（`JWT_SECRET`），不入 Git。
- 登录/注册接口套用现有 `@RateLimit`。
- 任何日志、响应不得出现密码与 token。

## 7. 参考实现（实施时优先照抄）

| 参考 | 抄什么 | 链接 |
|---|---|---|
| spring-projects/spring-security-samples · jwt/login | **官方**最小 JWT 登录示例：`SecurityFilterChain` 配置、登录签发 token 的写法 | [github.com/spring-projects/spring-security-samples](https://github.com/spring-projects/spring-security-samples/tree/main/servlet/spring-boot/java/jwt/login) |
| bezkoder/spring-boot-spring-security-jwt-authentication | 完整最小示例：注册/登录、`OncePerRequestFilter` 解析 JWT、`UserDetailsService` 对接 JPA 用户表、按角色控制 URL——与本项目结构最接近 | [github.com/bezkoder/spring-boot-spring-security-jwt-authentication](https://github.com/bezkoder/spring-boot-spring-security-jwt-authentication) |
| jwtk/jjwt | JWT 签发/解析 API 用法（官方 README 示例足够） | [github.com/jwtk/jjwt](https://github.com/jwtk/jjwt) |

**明确不参考**：Sa-Token、Spring Authorization Server、RuoYi 等完整权限框架——它们解决的是 RBAC 权限点、OAuth 服务端、多租户等本项目不存在的问题，引入即过度设计。

## 8. 被拒绝的替代方案

| 方案 | 拒绝理由 |
|---|---|
| Session + Cookie 认证 | 前后端分离 + 未来多端，无状态 JWT 更简单，还省掉服务端会话存储 |
| 保留前端传 candidateId，后端加签校验 | 身份来源仍是客户端，签名只防改不防伪，没解决生产者问题 |
| refresh token + 吊销机制 | 见第 1 节不做清单：多一张表和整套旋转/吊销逻辑，换不来 MVP 阶段需要的安全收益 |
| `@PreAuthorize` 逐方法标注 | 规则散落各处，审查权限矩阵要翻全部 Controller |
| 多租户 RBAC 权限模型 | 见第 2 节：两个角色用枚举即可 |

## 9. 交付切片建议

| 切片 | 内容 | 验收 |
|---|---|---|
| A0 | `users` 表、注册/登录/me 三个接口、JWT Filter、SecurityFilterChain 角色规则（照抄第 7 节参考实现） | 未登录访问业务接口返回 401；登录后可访问 |
| A1 | 候选人接口身份注入改造：移除请求体/路径中的 candidateId，Repository 归属条件化；前端移除候选人标识输入框 | 用 A 的 token 访问 B 的 sessionId/画像被拒 |
| A2 | ADMIN 角色 + `/api/llm-provider/**` 收敛 | 候选人访问 llm-provider 被拒 |

> 注：本文档仅覆盖设计；实施时按第 9 节切片拆任务，编码直接参照第 7 节的开源实现。
