---
paths:
  - "app/src/main/java/**/*.java"
  - "app/src/test/java/**/*.java"
  - "app/src/main/resources/**/*.yml"
---

# Backend Rules

## Layers

- Controller 只做路由、参数校验、限流注解和 Service 委托。
- Service 负责编排业务流程，事务注解只放 Service 层。
- Repository 只做数据访问，继承 `JpaRepository`。
- Infrastructure 能力放在 `common/` 或 `infrastructure/`，不要塞进业务模块。

## Naming

- JPA 持久化对象使用 `XxxEntity`。
- 跨层数据传输使用 `XxxDTO`。
- 前端请求体使用 `XxxRequest`，前端响应体使用 `XxxResponse`。
- 不可变请求/响应优先使用 `record`。

## Exceptions

- 业务失败使用 `BusinessException(ErrorCode.XXX, "描述信息")`。
- 保留业务异常原样抛出：`catch (BusinessException e) { throw e; }`。
- 禁止用 `RuntimeException` 表达业务错误。
- 禁止吞异常；记录异常时把异常对象作为日志最后一个参数。

## Transactions

- `@Transactional` 只放 Service 层公开方法。
- 事务内不得调用 LLM、S3、外部 HTTP 或耗时文件解析。
- 避免同类内部调用 `@Transactional` 方法，必要时拆分到独立 Service。
- 读多写少的查询使用 `@Transactional(readOnly = true)`。

## Mapping

- Entity 不直接返回给前端。
- Entity 到 DTO/Response 的重复转换优先使用 MapStruct。
- 简单一次性字段复制可以使用 `BeanUtils.copyProperties`。

## Minimal Implementation（最小实现）

- 密码哈希、JWT、加解密一律用框架组件（`BCryptPasswordEncoder`、`jjwt`），禁止自写或二次封装。
- 已从 `SecurityContext`/`@AuthenticationPrincipal` 获取的身份，不再校验「请求里的 ID 与登录身份是否一致」——身份参数不进请求体和路径（用 `/me/...`），从源头消除这类校验。
- 归属/存在性校验融进 Repository 查询（`findByIdAndCandidateId`，查不到即抛 `BusinessException`），不写「先 `findById` 判空再比对」的多步防御代码。
- 每个外部约束只校验一次：Controller 已用 Bean Validation 校验的字段，Service 不重复校验；Filter 层已做的认证，业务层不再怀疑。
- 设计新模块前先写「不做清单」，范例见 `docs/design_spec/02-auth-permission.md` §1。
- 写防御性代码前自问三句：防的是什么真实存在的问题？框架或上层是否已经防过了？删掉它测试会红吗？答不上来就不要写。
- 没有内部实现可复用时，优先参考 GitHub 上成熟的开源实现并裁剪，不自行设计协议、格式和机制。

### 典型无意义校验（安全感驱动，一律禁止）

- 不信任数据库约束：已有 `NOT NULL`/唯一索引/外键的字段，在代码里「先查重」「先判空」。让约束失败并转成业务异常即可。
- 不信任非空约定：入口已校验或内部创建的对象，在内部传递时反复判空、给框架保证非空的返回值写 `if null return`；`Optional` 只用于真正可缺省的查询结果，不当字段、参数和返回值用。
- 不信任编译期保证：穷尽的 switch 表达式再加 `default -> throw`；对枚举落地值做「未知值」防御。
- 兜底即吞错：解析/调用/缺配置失败时返回空集合、`null` 或「安全默认值」——这是吞错误的变体，失败必须抛出让调用方感知。
- 捕获处理不了的异常：`catch` 只为转换错误类型或补充上下文；禁止 `catch` 后仅记日志再原样重抛。
- 仪式性防御：请求作用域/单线程对象加锁、对内部数据做防御性拷贝（`Collections.unmodifiableXxx`、`new ArrayList<>(入参)`）。
- 无用户的抽象：只有一个实现、没有第二个调用方的接口不抽；不为「方便 mock」抽接口（Mockito 可以直接 mock 类）。

## Style

- Java 代码使用 2 空格缩进，列宽尽量控制在 100 字符。
- 禁止通配符导入，避免内联全限定类名。
- 优先使用构造器注入和 `@RequiredArgsConstructor`。
- 现代 Java 特性可用：switch 表达式、pattern matching、text blocks。
