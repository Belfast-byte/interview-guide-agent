#!/usr/bin/env python3
"""Synthetic-only live model sampling using production prompts and exported Java schemas.

Requires ANTHROPIC_BASE_URL, ANTHROPIC_AUTH_TOKEN and --model (an explicit API model ID).
First export schemas with ExportCodeRepairSchema.java using the app runtime classpath.
Uses no application database, real resume/JD, mocked response, retry, or output repair.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import json
import jsonschema
import os
from pathlib import Path
import time
import urllib.request

MAX_OUTPUT_TOKENS = 8192
REQUEST_TIMEOUT_SECONDS = 60
ROOT = Path(__file__).resolve().parents[2]
PROMPTS = ROOT / "app/src/main/resources/prompts"


def task():
    return {
        "initialCode": "void reserve(long skuId, int quantity) {\n    int available = stocks.findAvailable(skuId);\n    if (available < quantity) throw new IllegalStateException(\"库存不足\");\n    stocks.setAvailable(skuId, available - quantity);\n}",
        "requirements": ["成功预留总量不得超过库存", "库存不足时明确拒绝且不扣减"],
        "assumptions": ["合成验收示例：多个服务实例共享数据库", "skuId存在，quantity已经校验为正整数",
                        "findAvailable和setAvailable各是独立数据库语句",
                        "stocks.tryReserve(skuId, quantity)原子条件扣减，返回受影响行数，0表示库存不足"],
        "reviewGuide": {"checks": [
            {"id": "C1", "defect": "读取与写回分离可能超卖", "trigger": "多个实例并发预留", "acceptance": "数据库共享约束或等价机制保证扣减原子性"},
            {"id": "C2", "defect": "修复仍需拒绝库存不足", "trigger": "库存小于预留量", "acceptance": "明确拒绝且不改变库存"}]}}


def cases():
    planner = {"interview": {"jd": "合成JD：Java 21后端，负责订单库存，关注多实例并发一致性。",
        "resume": "合成简历：参与订单服务开发，使用Java及关系数据库，无真实个人数据。",
        "mode": "EVALUATION", "candidateLevel": "EXPERIENCED", "practiceScope": [],
        "skillCatalog": [{"skillId": "java-backend", "focusIds": ["JAVA"]}]}}
    correct = "void reserve(long skuId, int quantity) {\n    if (stocks.tryReserve(skuId, quantity) == 0) {\n        throw new IllegalStateException(\"库存不足\");\n    }\n}"
    wrong = "synchronized void reserve(long skuId, int quantity) {\n    int available = stocks.findAvailable(skuId);\n    if (available < quantity) throw new IllegalStateException(\"库存不足\");\n    stocks.setAvailable(skuId, available - quantity);\n}"
    partial = "void reserve(long skuId, int quantity) {\n    stocks.tryReserve(skuId, quantity);\n}"
    return [("planner", "planner", planner), *[(name, "assessment", {
        "dimension": "并发一致性", "focus": "多实例库存预留", "question": "合成题：修复共享库存预留缺陷。",
        "answer": None, "rubric": ["L0 无有效证据", "L1 术语和表面描述", "L2 机制与因果", "L3 权衡边界", "L4 系统性判断"],
        "adoptedRubrics": [], "openGaps": [], "priorTurns": [],
        "codeTaskContext": {"task": task(), "submittedCode": code, "priorReviews": []}})
        for name, code in [("correct", correct), ("wrong-instance-lock", wrong), ("partial-no-rejection", partial)]]]


def prompts(kind, data, schema_dir):
    system = (PROMPTS / f"adaptive-agent-{kind}-system.st").read_text()
    if kind == "assessment":
        system += "\n" + (PROMPTS / "adaptive-agent-assessment-agents.md").read_text()
        system += "\n技能基线：共享数据库上的跨实例并发不能由单实例synchronized保护；原子条件更新需判断结果。\n"
    system += "\n" + (schema_dir / f"{kind}-format.txt").read_text()
    system += (schema_dir / "security-instruction.txt").read_text()
    user = (PROMPTS / f"adaptive-agent-{kind}-user.st").read_text()
    user = user.replace("{inputJson}" if kind == "planner" else "{contextJson}", json.dumps(data, ensure_ascii=False))
    if kind == "planner":
        user = "本次是合成代码题验收场景，请首题选择CODE_REPAIR。\n" + user
    return system, user


def invoke(case, options):
    name, kind, data = case
    system, user = prompts(kind, data, options.schemas)
    payload = {"model": options.model, "max_tokens": MAX_OUTPUT_TOKENS, "system": system,
               "output_config": {"effort": options.effort},
               "messages": [{"role": "user", "content": user}]}
    request = urllib.request.Request(os.environ["ANTHROPIC_BASE_URL"].rstrip("/") + "/v1/messages",
        data=json.dumps(payload).encode(), headers={"content-type": "application/json", "anthropic-version": "2023-06-01",
        "x-api-key": os.environ["ANTHROPIC_AUTH_TOKEN"]})
    started = time.monotonic()
    with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
        result = json.load(response)
    text = "".join(block["text"] for block in result["content"] if block["type"] == "text")
    artifact = {"case": name, "requestedModel": options.model, "returnedModel": result.get("model"),
        "reasoningEffort": options.effort, "stopReason": result["stop_reason"], "usage": result.get("usage"),
        "elapsedSeconds": round(time.monotonic() - started, 2), "rawText": text}
    (options.output / f"{name}.json").write_text(json.dumps(artifact, ensure_ascii=False, indent=2))
    if result["stop_reason"] == "max_tokens":
        raise AssertionError(f"{name}: output truncated at configured token budget")
    output = json.loads(text)
    jsonschema.validate(output, json.loads((options.schemas / f"{kind}-schema.json").read_text()))
    checks = validate(name, data, output)
    artifact["validation"] = checks
    artifact.pop("rawText")
    print(json.dumps(artifact, ensure_ascii=False), flush=True)
    return artifact


def validate(name, data, output):
    if name == "planner":
        topics = [(item["suggestedSkill"], item["focusId"]) for item in output["dimensions"]]
        if len(topics) != len(set(topics)):
            raise AssertionError("planner repeated a topic forbidden by InterviewPlan")
        question = output["initialQuestion"]
        if question["questionType"] != "CODE_REPAIR" or question["codeTaskTurnIndex"] is not None:
            raise AssertionError("planner did not produce a new code task")
        generated = question["codeTask"]
        for field in ["initialCode", "requirements", "assumptions"]:
            if not generated[field]:
                raise AssertionError(f"empty generated {field}")
        return {"newCodeTask": True, "visibleContent": question["content"], "manualReviewRequired": True}
    sources = {"ANSWER_TEXT": data["answer"], "SUBMITTED_CODE": data["codeTaskContext"]["submittedCode"]}
    quotes = output["evidenceQuotes"] + [gap["anchor"] for gap in output["probeGaps"]]
    for quote in quotes:
        validate_quote(quote, sources)
    actual = {check["checkId"]: check["result"] for check in output["codeReview"]["checks"]}
    expected = {"correct": {"C1": "SATISFIED", "C2": "SATISFIED"},
        "wrong-instance-lock": {"C1": "NOT_SATISFIED", "C2": actual.get("C2")},
        "partial-no-rejection": {"C1": "SATISFIED", "C2": "NOT_SATISFIED"}}[name]
    # C2 shares the concurrency premise; retain its conclusion for semantic review.
    if len(output["codeReview"]["checks"]) != 2 or actual != expected:
        raise AssertionError(f"{name}: expected {expected}, received {actual}")
    return {"checkResults": actual, "exactSourceQuotes": len(quotes), "depthLevel": output["depthLevel"]}


def validate_quote(quote, sources):
    source = sources[quote["source"]]
    text = quote["quote"]
    if not text or source is None:
        raise AssertionError("missing quote or source")
    offset = quote.get("startOffset")
    if offset is None:
        if source.count(text) != 1:
            raise AssertionError("quote missing or ambiguous")
        return
    raw = source.encode("utf-16-le")
    selected = raw[offset * 2:offset * 2 + len(text.encode("utf-16-le"))].decode("utf-16-le")
    if selected != text:
        raise AssertionError("quote does not match UTF-16 location")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True)
    parser.add_argument("--effort", choices=["low", "high", "max"], default="low")
    parser.add_argument("--schemas", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--case", choices=[item[0] for item in cases()])
    options = parser.parse_args()
    options.output.mkdir(parents=True, exist_ok=True)
    selected = [item for item in cases() if options.case is None or item[0] == options.case]
    (options.output / "inputs.json").write_text(json.dumps({item[0]: item[2] for item in selected}, ensure_ascii=False, indent=2))
    results = []
    failures = []
    with ThreadPoolExecutor(max_workers=len(selected)) as executor:
        futures = {executor.submit(invoke, case, options): case[0] for case in selected}
        for future in as_completed(futures):
            try:
                results.append(future.result())
            except Exception as error:
                failures.append({"case": futures[future], "error": str(error)})
    summary = {"successes": results, "failures": failures}
    (options.output / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2))
    if failures:
        raise SystemExit(json.dumps(failures, ensure_ascii=False))



if __name__ == "__main__":
    main()
