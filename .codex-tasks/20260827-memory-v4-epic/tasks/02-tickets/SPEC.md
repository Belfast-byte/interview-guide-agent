# Task Specification：三层记忆 v4 实施 Tickets

## Task Shape

- **Shape**: `single-full`

## Goals

- 用 35 号文档直接覆盖 v3 tickets。
- 将 34 号规格拆成有明确依赖、删除范围、测试和提交边界的实施票据。

## Non-Goals

- 不实现代码。
- 不保留 v3 ticket、迁移票据或兼容工作。

## Constraints

- 每个 ticket 只交付一个可验证行为，整体按模块分组。
- ticket 必须指出复用的当前实现和同票删除的旧实现。
- 文档不超过 300 行，不预留未使用扩展点。

## Deliverables

- `docs/design_spec/35-memory-three-layer-tickets.md`

## Done-When

- [ ] 34 号规格的 10 条完成标准均映射到 ticket。
- [ ] 每个 ticket 有依赖、实现、删除、测试和提交边界。
- [ ] v3 ticket 内容全部删除。

## Final Validation Command

```bash
git diff --check && test $(wc -l < docs/design_spec/35-memory-three-layer-tickets.md) -le 300
```
