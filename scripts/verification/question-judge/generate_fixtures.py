#!/usr/bin/env python3
"""Generate synthetic TEXT fixtures and proposed labels outside Git; no candidate data."""
import json
import pathlib
import sys

cases = [
    ("backend-open", "比较缓存旁路模式与写穿模式的取舍。你可以先说明业务假设，再讨论一致性和延迟。", "缓存设计权衡", [], "开放讨论题，允许明确假设，不应因无唯一答案判错。"),
    ("backend-false-premise", "请证明任何负载下加索引都能提高数据库性能。", "索引收益与代价", ["TECHNICAL_CORRECTNESS"], "无条件全称命题错误；写负载维护索引存在成本。"),
    ("product-missing-data", "请仅根据本题条件算出这次转化率实验所需的唯一准确样本量，不得自行假设参数。", "实验设计", ["ANSWERABILITY"], "未提供基线、最小可检测效应、显著性和功效等必要参数。"),
    ("design-unique-answer", "不考虑产品类型、用户群和设备限制，请证明所有界面的最佳导航方式只能是底部标签栏。", "交互方案比较", ["ACCEPTANCE_FAIRNESS"], "无条件限定唯一方案，不能容纳合理情境差异。"),
    ("operations-unfocused", "同时详细解释数据库索引、品牌视觉、税务政策、机器学习优化器和仓库排班。", "仓库排班优化", ["FOCUS"], "无关考察点堆叠，无法聚焦目标。"),
    ("leading-answer", "LRU 会淘汰最近最少使用的条目。请问 LRU 的淘汰规则是什么？", "理解缓存淘汰规则", ["LEAKAGE"], "题面直接代答要验证的规则。"),
    ("valid-followup", "沿用刚才的旁路缓存方案，如果删除缓存失败，你会如何恢复并评估一致性风险？", "故障处理", [], "追问新的失败边界，与历史内容相似不等于无意重复。"),
    ("unintentional-repeat", "再说一次：旁路缓存的常规读取流程是什么？", "缓存常规读取", ["REPETITION"], "给定历史已充分验证相同机制，未引入新边界。"),
]
root = pathlib.Path(sys.argv[1])
root.mkdir(parents=True, exist_ok=True, mode=0o700)
samples, labels = [], []
for name, question, target, expected, rationale in cases:
    history = []
    if name in {"valid-followup", "unintentional-repeat"}:
        history = [{"turnIndex": 1, "question": "请解释旁路缓存的常规读取流程。",
                    "verification": "合成历史事实：候选人已完整解释命中返回、未命中查询数据库再回填；未验证删除失败场景。",
                    "verificationSource": "synthetic-history-v1"}]
    samples.append({"sampleId": name, "source": "FIXTURE", "turnIndex": 2 if history else 1,
                    "questionType": "TEXT", "publicQuestion": {"content": question, "reason": None, "context": []},
                    "target": target, "gap": None, "history": history, "historyComplete": True,
                    "generatorModel": "synthetic-author", "generatorPromptVersion": "synthetic-v1"})
    labels.append({"sampleId": name, "proposedIssueDimensions": expected, "rationale": rationale,
                   "humanReview": "PENDING", "labelKind": "PROPOSED_NOT_HUMAN_GROUND_TRUTH"})
for name, value in [("fixtures.json", samples), ("proposed-labels.json", labels)]:
    path = root / name
    with path.open("x", encoding="utf-8") as stream:
        path.chmod(0o600)
        json.dump(value, stream, ensure_ascii=False, indent=2)
print(root / "fixtures.json")
