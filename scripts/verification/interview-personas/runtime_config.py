"""Local configuration and read-only, account-scoped Provider access."""

import base64
import binascii
import getpass
import hashlib
import math
import os
from pathlib import Path
import re
import sys
import uuid

try:
    import psycopg
except ImportError:
    psycopg = None


class RunError(Exception):
    """Actionable failure without credentials or remote response bodies."""


ROOT = Path(__file__).resolve().parents[3]


def load_environment(path):
    """Match bootRun's literal KEY=VALUE file syntax; process env takes precedence."""
    if not path.is_file():
        raise RunError("指定的环境配置文件不存在")
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = (part.strip() for part in line.split("=", 1))
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", key):
            raise RunError("环境配置文件包含非法变量名")
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        os.environ.setdefault(key, value)


def authenticate(http):
    token = os.environ.get("INTERVIEW_API_TOKEN", "").strip()
    if token:
        return token
    email = os.environ.get("INTERVIEW_EMAIL", "").strip()
    password = os.environ.get("INTERVIEW_PASSWORD", "")
    if not email or not password:
        if not sys.stdin.isatty():
            raise RunError("请设置 INTERVIEW_API_TOKEN，或 INTERVIEW_EMAIL 和 INTERVIEW_PASSWORD")
        if not email:
            email = input("测试账号邮箱：").strip()
        if not password:
            password = getpass.getpass("测试账号密码：")
    if not email or not password:
        raise RunError("登录邮箱和密码不能为空")
    data = api_data(http.request("/api/auth/login", {"email": email, "password": password}))
    token = data.get("accessToken")
    if not isinstance(token, str) or not token.strip():
        raise RunError("登录响应缺少 accessToken")
    return token


def api_data(result):
    if result.get("code") != 200 or not isinstance(result.get("data"), dict):
        raise RunError("认证 API 失败，请检查登录信息和服务地址")
    return result["data"]


def connect_database():
    if psycopg is None:
        raise RunError("数据库模式需要安装 scripts/verification/interview-personas/requirements.txt")
    # These defaults match application.yml; credentials never appear in command arguments.
    settings = dict(user=os.environ.get("SPRING_DATASOURCE_USERNAME", os.environ.get("POSTGRES_USER", "postgres")),
                    password=os.environ.get("SPRING_DATASOURCE_PASSWORD", os.environ.get("POSTGRES_PASSWORD", "123456")),
                    connect_timeout=10, options="-c default_transaction_read_only=on -c statement_timeout=10000")
    url = os.environ.get("SPRING_DATASOURCE_URL")
    if url:
        if not url.startswith("jdbc:postgresql://"):
            raise RunError("SPRING_DATASOURCE_URL 必须是 jdbc:postgresql:// 地址")
        return psycopg.connect(url[len("jdbc:"):], **settings)
    return psycopg.connect(host=os.environ.get("POSTGRES_HOST", "localhost"),
                           port=os.environ.get("POSTGRES_PORT", "5432"),
                           dbname=os.environ.get("POSTGRES_DB", "interview_guide"), **settings)


def decrypt_key(nonce, ciphertext):
    try:
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    except ImportError:
        raise RunError("数据库模式需要安装 scripts/verification/interview-personas/requirements.txt") from None
    key = os.environ.get("APP_AI_CONFIG_ENCRYPTION_KEY", "").strip()
    if not key:
        required = os.environ.get("APP_AI_CONFIG_REQUIRE_ENCRYPTION_KEY", "true").lower() == "true"
        allowed = os.environ.get("APP_AI_CONFIG_ALLOW_FALLBACK_ENCRYPTION_KEY", "false").lower() == "true"
        if required or not allowed:
            raise RunError("请提供与后端一致的 APP_AI_CONFIG_ENCRYPTION_KEY")
        # The backend's existing, explicitly enabled development-key policy.
        key = "interview-guide-dev-only-provider-api-key-encryption"
    try:
        # Java Base64.Decoder also accepts omitted trailing padding.
        raw = base64.b64decode(key + "=" * (-len(key) % 4), validate=True)
    except (ValueError, binascii.Error):
        raw = b""
    if len(raw) != 32:
        raw = hashlib.sha256(key.encode()).digest()
    try:
        plain = AESGCM(raw).decrypt(base64.b64decode(nonce, validate=True),
                                    base64.b64decode(ciphertext, validate=True), None).decode()
    except Exception:
        raise RunError("Provider API key 解密失败，请核对数据库与后端加密密钥") from None
    if not plain.strip():
        raise RunError("Provider API key 为空")
    return plain


def database_provider(candidate_id, provider_id, simulator_id, need_simulator):
    """Select only the authenticated account's enabled rows; never guess an account/default."""
    try:
        with connect_database() as connection:
            connection.read_only = True
            with connection.cursor() as cursor:
                if not provider_id:
                    cursor.execute("SELECT default_chat_provider_id FROM candidate_llm_settings WHERE candidate_id = %s",
                                   (candidate_id,))
                    row = cursor.fetchone()
                    if not row or not row[0]:
                        raise RunError("账号未设置默认文本 Provider，请在平台设置或传入 --provider")
                    provider_id = row[0]
                rows = []
                for selected in ([provider_id, simulator_id or provider_id] if need_simulator else [provider_id]):
                    cursor.execute("""SELECT base_url, api_key_nonce, api_key_ciphertext, model,
                                             temperature, thinking_disabled
                                      FROM llm_provider_config
                                      WHERE candidate_id = %s AND id = %s AND enabled = TRUE""",
                                   (candidate_id, selected))
                    row = cursor.fetchone()
                    if not row:
                        raise RunError("Provider 不属于当前账号、未启用或不存在")
                    rows.append(row)
    except RunError:
        raise
    except Exception as error:
        if psycopg is None or not isinstance(error, psycopg.Error):
            raise
        raise RunError(f"读取 Provider 数据库失败（{type(error).__name__}，SQLSTATE={error.sqlstate or '无'}），"
                       "请检查数据库配置、连接和表结构") from None
    if not need_simulator:
        return provider_id, None
    base, nonce, ciphertext, model, temperature, thinking_disabled = rows[-1]
    if not isinstance(base, str) or not base.strip() or not isinstance(model, str) or not model.strip():
        raise RunError("数据库 Provider 缺少 base_url 或 model")
    if temperature is not None and (type(temperature) not in (int, float) or not math.isfinite(temperature)):
        raise RunError("数据库 Provider temperature 非法")
    if type(thinking_disabled) is not bool:
        raise RunError("数据库 Provider thinking_disabled 非法")
    base = base.strip().rstrip("/")
    # Same version suffix rule as ApiPathResolver.resolveVersionedBaseUrl.
    if not re.search(r"/v\d+[a-zA-Z0-9]*$", base):
        base += "/v1"
    parameters = {"temperature": temperature if temperature is not None else 0.2}
    if thinking_disabled:
        parameters["thinking"] = {"type": "disabled"}
    return provider_id, (base, decrypt_key(nonce, ciphertext), model, parameters)


def resolve_models(options, http):
    names = ("SIMULATOR_BASE_URL", "SIMULATOR_API_KEY", "SIMULATOR_MODEL")
    explicit = [os.environ.get(name, "").strip() for name in names]
    if any(explicit) and not all(explicit):
        raise RunError("显式候选人配置必须同时提供全部 SIMULATOR_BASE_URL/API_KEY/MODEL")
    if all(explicit) and options.simulator_provider:
        raise RunError("--simulator-provider 不能与 SIMULATOR_* 同时使用")
    if all(explicit) and options.provider:
        return options.provider, (*explicit, {})
    user = api_data(http.request("/api/auth/me"))
    try:
        candidate_id = str(uuid.UUID(user["candidateId"]))
    except (KeyError, ValueError, TypeError, AttributeError):
        raise RunError("认证响应缺少合法 candidateId") from None
    provider, simulator = database_provider(candidate_id, options.provider, options.simulator_provider, not all(explicit))
    return provider, (*explicit, {}) if all(explicit) else simulator
