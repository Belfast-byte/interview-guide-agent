"""Configuration boundary tests; no real account, database or model calls."""
import base64
import hashlib
import io
import os
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import MagicMock, patch

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

import runtime_config as config
import run_interviews as runner
from test_run_interviews import http_fixture


class ConfigurationTests(unittest.TestCase):
    def test_env_is_literal_and_process_values_win(self):
        with tempfile.TemporaryDirectory() as folder, patch.dict(os.environ, {"EXISTING": "process"}, clear=True):
            path = Path(folder) / ".env"
            path.write_text('# comment\nEXISTING=file\nQUOTED="a=b $value"\nEMPTY=\n')
            config.load_environment(path)
            self.assertEqual("process", os.environ["EXISTING"])
            self.assertEqual("a=b $value", os.environ["QUOTED"])
            self.assertEqual("", os.environ["EMPTY"])

    def test_login_and_existing_token(self):
        http = MagicMock()
        http.request.return_value = {"code": 200, "data": {"accessToken": "fixture-token"}}
        with patch.dict(os.environ, {"INTERVIEW_EMAIL": "fixture@example.test", "INTERVIEW_PASSWORD": "fixture-password"}, clear=True):
            self.assertEqual("fixture-token", config.authenticate(http))
        http.request.assert_called_once_with("/api/auth/login", {"email": "fixture@example.test", "password": "fixture-password"})
        http.reset_mock()
        with patch.dict(os.environ, {"INTERVIEW_API_TOKEN": "existing"}, clear=True):
            self.assertEqual("existing", config.authenticate(http))
        http.request.assert_not_called()

    def test_decryption_text_base64_and_wrong_key(self):
        nonce = bytes(range(12))
        for key in ("synthetic-test-key", base64.b64encode(bytes(range(32))).decode(),
                    base64.b64encode(bytes(range(32))).decode().rstrip("=")):
            raw = bytes(range(32)) if key != "synthetic-test-key" else hashlib.sha256(key.encode()).digest()
            cipher = base64.b64encode(AESGCM(raw).encrypt(nonce, b"fixture-api-key", None)).decode()
            with patch.dict(os.environ, {"APP_AI_CONFIG_ENCRYPTION_KEY": key}, clear=True):
                self.assertEqual("fixture-api-key", config.decrypt_key(base64.b64encode(nonce).decode(), cipher))
            with patch.dict(os.environ, {"APP_AI_CONFIG_ENCRYPTION_KEY": "wrong-key"}, clear=True):
                with self.assertRaisesRegex(config.RunError, "解密失败"):
                    config.decrypt_key(base64.b64encode(nonce).decode(), cipher)
        with patch.dict(os.environ, {}, clear=True), self.assertRaisesRegex(config.RunError, "APP_AI_CONFIG_ENCRYPTION_KEY"):
            config.decrypt_key("unused", "unused")

    def test_provider_queries_are_scoped_and_parameters_preserved(self):
        for base, expected in (("https://example.test", "https://example.test/v1"),
                               ("https://example.test/api/v4/", "https://example.test/api/v4")):
            connection = MagicMock()
            cursor = connection.__enter__.return_value.cursor.return_value.__enter__.return_value
            provider_row = (base, "nonce", "cipher", "model", 0.0, True)
            cursor.fetchone.side_effect = [("default-id",), provider_row, provider_row]
            with patch.object(config, "connect_database", return_value=connection), patch.object(config, "decrypt_key", return_value="fixture-key"):
                provider, simulator = config.database_provider("account-a", None, "sim-id", True)
            self.assertEqual("default-id", provider)
            self.assertEqual((expected, "fixture-key", "model", {"temperature": 0.0, "thinking": {"type": "disabled"}}), simulator)
            calls = cursor.execute.call_args_list
            self.assertEqual(("account-a",), calls[0].args[1])
            self.assertEqual(("account-a", "default-id"), calls[1].args[1])
            self.assertEqual(("account-a", "sim-id"), calls[2].args[1])
            self.assertIn("candidate_id = %s AND id = %s AND enabled = TRUE", calls[2].args[0])
            entity = (config.ROOT / "app/src/main/java/interview/guide/modules/llmprovider/model/LlmProviderEntity.java").read_text()
            self.assertIn('name = "llm_provider_config"', entity)
            self.assertIn("FROM llm_provider_config", calls[2].args[0])
            connection.__exit__.assert_called_once()

    def test_missing_default_or_unavailable_provider_fails(self):
        for selected in (None, "foreign-or-disabled"):
            connection = MagicMock()
            connection.__enter__.return_value.cursor.return_value.__enter__.return_value.fetchone.return_value = None
            with patch.object(config, "connect_database", return_value=connection), self.assertRaises(config.RunError):
                config.database_provider("account-a", selected, None, True)

    def test_database_programming_errors_are_not_hidden(self):
        with patch.object(config, "connect_database", side_effect=TypeError("implementation error")):
            with self.assertRaisesRegex(TypeError, "implementation error"):
                config.database_provider("account-a", "provider", None, True)

    def test_partial_explicit_config_does_not_fall_back(self):
        with patch.dict(os.environ, {"SIMULATOR_MODEL": "model"}, clear=True), self.assertRaisesRegex(config.RunError, "同时提供"):
            config.resolve_models(SimpleNamespace(provider=None, simulator_provider=None), MagicMock())

    def test_list_needs_neither_env_nor_auth(self):
        with patch.object(runner, "load_environment") as load, patch.object(runner, "authenticate") as auth, patch("sys.stdout", new=io.StringIO()):
            self.assertEqual(0, runner.main(["--env-file", "/not-present", "--list"]))
        load.assert_not_called()
        auth.assert_not_called()

    def test_database_mode_completes_existing_interview_flow(self):
        candidate = "00000000-0000-0000-0000-000000000001"
        original_request = runner.JsonHttp.request

        def request(http, path, payload=None):
            if path == "/api/auth/login":
                self.assertIsNone(http.token)
                return {"code": 200, "data": {"accessToken": "fixture-app-token"}}
            if path == "/api/auth/me":
                self.assertEqual("fixture-app-token", http.token)
                return {"code": 200, "data": {"candidateId": candidate}}
            return original_request(http, path, payload)

        with http_fixture() as (base, state), tempfile.TemporaryDirectory() as output:
            with patch.dict(os.environ, {"INTERVIEW_EMAIL": "fixture@example.test", "INTERVIEW_PASSWORD": "fixture-password"}, clear=True), \
                 patch.object(runner.JsonHttp, "request", request), \
                 patch.object(config, "database_provider", return_value=("db-provider", (base + "/v1", "fixture-db-key", "db-model", {"temperature": 0.0, "thinking": {"type": "disabled"}}))) as database, \
                 patch("sys.stdout", new=io.StringIO()):
                code = runner.main(["--no-env-file", "--api-base", base, "--persona", "concise", "--output", output])
            self.assertEqual(0, code)
            database.assert_called_once_with(candidate, None, None, True)
            for _, path, payload, authorization in state["requests"]:
                if path == "/v1/chat/completions":
                    self.assertEqual("Bearer fixture-db-key", authorization)
                    self.assertEqual(0, payload["temperature"])
                    self.assertEqual({"type": "disabled"}, payload["thinking"])
                if path == runner.API_PATH:
                    self.assertEqual("db-provider", payload["providerId"])
            for artifact in Path(output).rglob("*"):
                if artifact.is_file():
                    content = artifact.read_text(encoding="utf-8-sig")
                    for secret in ("fixture-db-key", "fixture-app-token", "fixture-password"):
                        self.assertNotIn(secret, content)


if __name__ == "__main__":
    unittest.main()
