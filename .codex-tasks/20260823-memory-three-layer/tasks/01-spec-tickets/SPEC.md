# T01 — 关闭 v3 spec 并输出 tickets

## Task Shape

- **Shape**: `single-full`

## Goals

- 把已确认的关闭方案完整写入 34 号 spec。
- 输出按依赖排序的 tickets，每个 ticket 包含 Goal、实现范围和测试验证。

## Non-Goals

- 本子任务不修改生产代码和数据库。

## Constraints

- 不保留“实现时定”一类会改变 schema、算法或事务的未决项。
- 文档必须与当前一问一答 turn、L0~L4 Assessment、动态 TopicKey 模型一致。

## Deliverables

- `docs/design/34-memory-three-layer-spec.md` v3。
- `docs/design/35-memory-three-layer-tickets.md`。

## Done-When

- [ ] v3 明确 WorkingMemorySnapshot、Turn Trigger、EpisodeFact/enrichment、Semantic 计数器、Prompt 消费者和迁移方案。
- [ ] tickets 与父 Epic 依赖一致。
- [ ] 文档链接和关键术语检查通过。
## Final Validation Command

```bash
rg -n '状态：.*v3|T01|T09|EpisodeFact|WorkingMemorySnapshot' docs/design/34-memory-three-layer-spec.md docs/design/35-memory-three-layer-tickets.md
```
