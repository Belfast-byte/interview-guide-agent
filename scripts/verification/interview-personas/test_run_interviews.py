"""Offline contract tests only. The HTTP fixtures do not test model quality."""

from contextlib import contextmanager, redirect_stdout, redirect_stderr
from copy import deepcopy
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import io
import json
import os
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch

import run_interviews as runner


def turn(index=1, kind="TEXT", status="WAITING"):
    return {
        "turnIndex": index, "dimensionOrder": 0,
        "question": "请解释这段代码的并发行为。", "questionType": kind,
        "answerStatus": status, "answer": None, "submittedCode": None,
        "codeTaskTurnIndex": 1 if kind == "CODE_REPAIR" else None,
        "codeTask": {"initialCode": "int counter = 0;", "requirements": ["共享计数"],
                     "assumptions": ["多线程"], "reviewGuide": "PRIVATE_GUIDE"}
        if kind == "CODE_REPAIR" else None,
        "assessmentFeedback": {"rationale": "PRIVATE_FEEDBACK"},
        "codeReview": "PRIVATE_REVIEW",
    }


def session(sid="fixture-session", kind="TEXT"):
    return {"sessionId": sid, "status": "IN_PROGRESS", "currentTurn": 1,
            "maxTurns": 10, "llmModel": "protocol-fixture", "runtimeVersion": "fixture",
            "dimensions": [
                {"order": 0, "dimension": "Java 并发", "focus": "线程安全", "expectedDepth": "L3"},
                {"order": 1, "dimension": "MySQL", "focus": "事务"}],
            "turns": [turn(kind=kind)]}


def report(sid="fixture-session"):
    return {"sessionId": sid, "dimensions": [
        {"order": 0, "dimension": "Java 并发", "focus": "线程安全", "depthLevel": "L2",
         "confidence": 0.8, "rationale": "协议测试用固定等级，不表示模型表现", "evidences": []},
        {"order": 1, "dimension": "MySQL", "focus": "事务", "depthLevel": None,
         "confidence": None, "rationale": "未考察", "evidences": []}]}


@contextmanager
def http_fixture(fail_submit=False):
    """Local transport fixture, never a selectable runtime simulation mode."""
    state = {"sessions": {}, "requests": [], "modelCalls": 0}

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def respond(self, payload, status=200):
            data = json.dumps(payload, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            self.dispatch(None)

        def do_POST(self):
            self.dispatch(json.loads(self.rfile.read(int(self.headers["Content-Length"]))))

        def dispatch(self, payload):
            state["requests"].append((self.command, self.path, payload, self.headers.get("Authorization")))
            if self.path == "/v1/chat/completions":
                state["modelCalls"] += 1
                question = json.loads(payload["messages"][-1]["content"])
                output = {"answer": "仅用于验证传输协议的回答", "code": None}
                if question["questionType"] == "CODE_REPAIR":
                    output["code"] = question["codeTask"]["initialCode"]
                self.respond({"model": "protocol-fixture", "usage": {"total_tokens": 10},
                              "choices": [{"finish_reason": "stop", "message": {
                                  "content": json.dumps(output), "reasoning_content": "PRIVATE_REASONING"}}]})
                return
            if self.path == runner.API_PATH and self.command == "POST":
                sid = f"fixture-{len(state['sessions']) + 1}"
                state["sessions"][sid] = session(sid, "CODE_REPAIR")
                self.respond({"code": 200, "data": state["sessions"][sid]})
                return
            parts = self.path[len(runner.API_PATH) + 1:].split("/")
            sid = parts[0]
            if sid not in state["sessions"]:
                self.respond({"code": 404}, 404)
                return
            current_session = state["sessions"][sid]
            if parts[-1] == "report":
                self.respond({"code": 200, "data": report(sid)})
            elif parts[-1] == "answers":
                if fail_submit:
                    self.respond({"code": 500, "message": "private backend detail", "data": None})
                    return
                current = current_session["turns"][-1]
                current.update(answerStatus="COMPLETED", answer=payload["answer"],
                               submittedCode=payload.get("codeRepair", {}).get("code"))
                if payload["turnIndex"] == 1:
                    followup = turn(2)
                    followup["codeTask"] = deepcopy(current["codeTask"])
                    followup["codeTaskTurnIndex"] = 1
                    current_session["turns"].append(followup)
                    current_session["currentTurn"] = 2
                else:
                    current_session["status"] = "COMPLETED"
                self.respond({"code": 200, "data": current_session})
            else:
                self.respond({"code": 200, "data": current_session})

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_port}", state
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)


class CandidateBoundaryTests(unittest.TestCase):
    def test_recursive_projection_excludes_grading_and_keeps_code_history(self):
        state = session(kind="CODE_REPAIR")
        first = state["turns"][0]
        first.update(answerStatus="COMPLETED", answer="原说明", submittedCode="int revised = 1;")
        followup = turn(2)
        followup.update(codeTask=first["codeTask"], codeTaskTurnIndex=1)
        state.update(currentTurn=2, turns=[first, followup])
        messages = runner.candidate_messages("role", state)
        serialized = json.dumps(messages, ensure_ascii=False)
        self.assertNotIn("PRIVATE_", serialized)
        self.assertNotIn("expectedDepth", serialized)
        self.assertIn("int revised = 1;", serialized)
        self.assertIn("initialCode", messages[-1]["content"])
        self.assertEqual("TEXT", json.loads(messages[-1]["content"])["questionType"])

    def test_pending_failed_or_previously_accepted_answers_are_not_regenerated(self):
        for status in ("PROCESSING", "RETRYABLE", "COMPLETED"):
            with self.subTest(status=status):
                state = session()
                state["turns"][0]["answerStatus"] = status
                with self.assertRaises(runner.RunError):
                    runner.candidate_messages("role", state)
        state = session()
        state["turns"][0]["answer"] = "已接受答案"
        with self.assertRaises(runner.RunError):
            runner.candidate_messages("role", state)

    def test_no_code_fallback_or_extra_answer_fields(self):
        for output, kind in [({"answer": "x", "code": None}, "CODE_REPAIR"),
                             ({"answer": "x", "code": "repair"}, "TEXT"),
                             ({"answer": "", "code": None}, "TEXT"),
                             ({"answer": "x", "code": None, "grade": "L4"}, "TEXT")]:
            with self.subTest(output=output, kind=kind), self.assertRaises(runner.RunError):
                runner.answer_payload(output, turn(kind=kind))

    def test_text_followup_and_code_repair_use_distinct_protocols(self):
        self.assertEqual({"turnIndex": 1, "answer": "解释"},
                         runner.answer_payload({"answer": "解释", "code": None}, turn()))
        self.assertEqual({"code": "int x = 1;"}, runner.answer_payload(
            {"answer": "", "code": "int x = 1;"}, turn(kind="CODE_REPAIR"))["codeRepair"])

    def test_truncated_model_output_is_saved_but_not_submitted(self):
        class TruncatedHttp:
            def request(self, *_):
                return {"choices": [{"finish_reason": "length", "message": {"content": '{"answer":'}}]}
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(runner.RunError):
                runner.simulate(TruncatedHttp(), "fixture", 10, [], turn(), Path(folder))
            artifact = json.loads((Path(folder) / "turn-001-simulator.json").read_text())
            self.assertEqual("length", artifact["finishReason"])

    def test_missing_or_referenced_code_material_is_rejected(self):
        current = turn(kind="CODE_REPAIR")
        current["codeTask"] = None
        with self.assertRaises(runner.RunError):
            runner.question_material(current)
        current["questionType"] = "TEXT"
        with self.assertRaises(runner.RunError):
            runner.question_material(current)


class ReportTests(unittest.TestCase):
    def setUp(self):
        self.session = session()
        self.session["status"] = "COMPLETED"

    def test_unassessed_stays_null_not_l0_or_zero(self):
        rows = runner.report_rows(report(), self.session, "concise")
        self.assertIsNone(rows[1]["depthLevel"])
        self.assertIsNone(rows[1]["confidence"])
        with tempfile.TemporaryDirectory() as folder:
            runner.write_scores(Path(folder), rows)
            self.assertIn("未考察", (Path(folder) / "scores.md").read_text())
            self.assertNotIn("L0", (Path(folder) / "scores.csv").read_text(encoding="utf-8-sig"))

    def test_incomplete_wrong_session_or_missing_dimensions_cannot_be_final(self):
        self.session["status"] = "IN_PROGRESS"
        with self.assertRaises(runner.RunError):
            runner.report_rows(report(), self.session, "concise")
        self.session["status"] = "COMPLETED"
        for mutation in ("session", "missing", "duplicate", "renamed", "no_level", "invalid_level"):
            value = report()
            if mutation == "session":
                value["sessionId"] = "another-session"
            elif mutation == "missing":
                value["dimensions"].pop()
            elif mutation == "duplicate":
                value["dimensions"].append(value["dimensions"][0])
            elif mutation == "renamed":
                value["dimensions"][0]["focus"] = "another focus"
            elif mutation == "no_level":
                del value["dimensions"][0]["depthLevel"]
            else:
                value["dimensions"][0]["depthLevel"] = "100"
            with self.subTest(mutation=mutation), self.assertRaises(runner.RunError):
                runner.report_rows(value, self.session, "concise")

    def test_csv_formula_and_markdown_cells_are_escaped(self):
        rows = runner.report_rows(report(), self.session, "concise")
        rows[0]["rationale"] = '=HYPERLINK("x")|<script>\nnewline'
        with tempfile.TemporaryDirectory() as folder:
            runner.write_scores(Path(folder), rows)
            self.assertIn("'=HYPERLINK", (Path(folder) / "scores.csv").read_text(encoding="utf-8-sig"))
            markdown = (Path(folder) / "scores.md").read_text()
            self.assertIn("&#124;&lt;script&gt;<br>newline", markdown)
            self.assertEqual(rows, json.loads((Path(folder) / "scores.json").read_text()))


class IntegrationTests(unittest.TestCase):
    def invoke(self, base, output, *args):
        environment = {"INTERVIEW_API_TOKEN": "test-app-token", "INTERVIEW_PROVIDER_ID": "test-provider",
                       "SIMULATOR_BASE_URL": base + "/v1", "SIMULATOR_API_KEY": "test-model-token",
                       "SIMULATOR_MODEL": "protocol-fixture"}
        with patch.dict(os.environ, environment), redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            code = runner.main(["--no-env-file", "--api-base", base, "--output", str(output), *args])
        return code, next(Path(output).iterdir())

    def test_all_five_cases_export_official_module_ratings(self):
        with http_fixture() as (base, state), tempfile.TemporaryDirectory() as output:
            code, root = self.invoke(base, output, "--persona", "all")
            self.assertEqual(0, code)
            summary = json.loads((root / "summary.json").read_text())
            self.assertEqual(5, summary["completed"])
            self.assertEqual(0, summary["failed"])
            self.assertEqual(10, len(json.loads((root / "scores.json").read_text())))
            for persona in runner.PERSONAS:
                case = root / persona
                system = (case / "simulator-system.txt").read_text()
                self.assertEqual(persona in runner.KNOWLEDGEABLE, "虚构项目事实册" in system)
                self.assertEqual("COMPLETED", json.loads((case / "status.json").read_text())["status"])
                self.assertTrue((case / "report.json").exists())
            for method, path, payload, authorization in state["requests"]:
                if path == "/v1/chat/completions":
                    self.assertEqual("Bearer test-model-token", authorization)
                    self.assertNotIn("PRIVATE_", json.dumps(payload))
                else:
                    self.assertEqual("Bearer test-app-token", authorization)
                if path == runner.API_PATH and method == "POST":
                    self.assertEqual({"jd", "resume", "providerId", "mode", "candidateLevel", "practiceScope"}, set(payload))
                    self.assertEqual("EVALUATION", payload["mode"])
                if path.endswith("/answers"):
                    self.assertEqual(payload["turnIndex"] == 1, "codeRepair" in payload)
            for artifact in root.rglob("*"):
                if artifact.is_file():
                    content = artifact.read_text(encoding="utf-8-sig")
                    self.assertNotIn("test-app-token", content)
                    self.assertNotIn("test-model-token", content)
                    self.assertNotIn("PRIVATE_REASONING", content)

    def test_business_failure_preserves_submission_and_does_not_retry(self):
        with http_fixture(fail_submit=True) as (base, state), tempfile.TemporaryDirectory() as output:
            code, root = self.invoke(base, output, "--persona", "novice")
            self.assertEqual(1, code)
            case = root / "novice"
            self.assertEqual("SUBMIT", json.loads((case / "status.json").read_text())["phase"])
            self.assertTrue((case / "turn-001-answer.json").exists())
            self.assertFalse((case / "report.json").exists())
            self.assertEqual([], json.loads((root / "scores.json").read_text()))
            self.assertEqual(1, state["modelCalls"])
            self.assertEqual(1, sum(path.endswith("/answers") for _, path, _, _ in state["requests"]))

    def test_collect_is_read_only_and_requires_completed_session(self):
        with http_fixture() as (base, state), tempfile.TemporaryDirectory() as output:
            state["sessions"]["fixture-session"] = session()
            code, root = self.invoke(base, output, "--collect", "fixture-session")
            self.assertEqual(1, code)
            self.assertFalse((root / "collected" / "report.json").exists())
            self.assertEqual(0, state["modelCalls"])
            self.assertTrue(all(method == "GET" for method, _, _, _ in state["requests"]))
        with http_fixture() as (base, state), tempfile.TemporaryDirectory() as output:
            state["sessions"]["fixture-session"] = session()
            state["sessions"]["fixture-session"]["status"] = "COMPLETED"
            code, root = self.invoke(base, output, "--collect", "fixture-session")
            self.assertEqual(0, code)
            self.assertTrue((root / "collected" / "scores.md").exists())
            self.assertEqual(0, state["modelCalls"])

    def test_http_error_and_redirect_are_not_retried(self):
        with http_fixture() as (base, state), tempfile.TemporaryDirectory() as output:
            http = runner.JsonHttp(base, "test", 2, Path(output) / "requests.jsonl")
            with self.assertRaisesRegex(runner.RunError, "HTTP 404"):
                http.request("/missing")
            self.assertEqual(1, len(state["requests"]))
            self.assertIsNone(runner.NoRedirect().redirect_request(None, None, 302, None, None, base))

    def test_missing_configuration_fails_before_creating_artifacts(self):
        with tempfile.TemporaryDirectory() as output, patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(runner.RunError, "INTERVIEW_API_TOKEN"):
                runner.main(["--no-env-file", "--output", output])
            self.assertEqual([], list(Path(output).iterdir()))

    def test_interrupt_preserves_case_and_marks_unstarted_cases(self):
        with http_fixture() as (base, state), tempfile.TemporaryDirectory() as output:
            with patch.object(runner, "simulate", side_effect=KeyboardInterrupt):
                code, root = self.invoke(base, output, "--persona", "all")
            self.assertEqual(130, code)
            summary = json.loads((root / "summary.json").read_text())
            self.assertEqual("INTERRUPTED", summary["runs"][0]["status"])
            self.assertEqual(4, len(summary["notStarted"]))
            self.assertEqual(0, summary["completed"])
            self.assertFalse(any(path.endswith("/answers") for _, path, _, _ in state["requests"]))

    def test_interrupt_after_completion_does_not_duplicate_run_count(self):
        original = runner.write_scores
        interrupted = False

        def interrupt_first_aggregate(folder, rows):
            nonlocal interrupted
            if folder.name not in runner.PERSONAS and not interrupted:
                interrupted = True
                raise KeyboardInterrupt
            original(folder, rows)

        with http_fixture() as (base, _), tempfile.TemporaryDirectory() as output:
            with patch.object(runner, "write_scores", side_effect=interrupt_first_aggregate):
                code, root = self.invoke(base, output, "--persona", "all")
            self.assertEqual(130, code)
            summary = json.loads((root / "summary.json").read_text())
            self.assertEqual(1, len(summary["runs"]))
            self.assertEqual(1, summary["completed"])
            self.assertEqual(4, len(summary["notStarted"]))


if __name__ == "__main__":
    unittest.main()
