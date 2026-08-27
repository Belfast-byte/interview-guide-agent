# Task Specification：三层记忆 v4 技术规格

## Task Shape

- **Shape**: `single-full`

## Goals

- 用 34 号文档直接取代旧三层记忆规格。
- 按当前代码和表结构区分“已有事实”与“目标设计”。
- 明确正式评估、练习、Working、Episode、Semantic 和 ActionIntent 的实现边界。

## Non-Goals

- 不写实现代码和实施 tickets。
- 不设计历史数据迁移、兼容层或通用安全治理。

## Constraints

- 上游唯一设计输入是 `docs/design/02-memory-design.md`。
- 文档不超过 300 行，表名和现状必须能在代码库中核对。
- 规格只保留当前项目需要的最小模型。

## Deliverables

- `docs/design_spec/34-memory-three-layer-spec.md`
- 相关规格索引和旧基线声明

## Done-When

- [ ] 34 号规格通过静态检查和逐项设计核对。
- [ ] 旧 v3 规格不再被索引为当前实施依据。
- [ ] 本子任务按主题提交。

## Final Validation Command

```bash
git diff --check && test $(wc -l < docs/design_spec/34-memory-three-layer-spec.md) -le 300
```
