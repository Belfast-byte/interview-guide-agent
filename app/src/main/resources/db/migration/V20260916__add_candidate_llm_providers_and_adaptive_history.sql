ALTER TABLE llm_provider_config
  ADD COLUMN candidate_id UUID;

ALTER TABLE llm_provider_config
  ADD COLUMN display_name VARCHAR(128);

ALTER TABLE llm_provider_config
  ADD CONSTRAINT fk_llm_provider_candidate
  FOREIGN KEY (candidate_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE llm_provider_config
  ADD CONSTRAINT ck_llm_provider_candidate_name
  CHECK (candidate_id IS NULL OR (display_name IS NOT NULL AND BTRIM(display_name) <> ''));

ALTER TABLE llm_provider_config
  ADD CONSTRAINT uk_llm_provider_candidate_id
  UNIQUE (candidate_id, id);

CREATE UNIQUE INDEX uk_llm_provider_candidate_name
  ON llm_provider_config (candidate_id, display_name);

CREATE INDEX idx_llm_provider_candidate_created
  ON llm_provider_config (candidate_id, created_at DESC);

CREATE TABLE candidate_llm_settings (
  candidate_id UUID PRIMARY KEY,
  default_chat_provider_id VARCHAR(64),
  default_embedding_provider_id VARCHAR(64),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_candidate_llm_setting_user
    FOREIGN KEY (candidate_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_candidate_default_chat_provider
    FOREIGN KEY (candidate_id, default_chat_provider_id)
    REFERENCES llm_provider_config(candidate_id, id),
  CONSTRAINT fk_candidate_default_embedding_provider
    FOREIGN KEY (candidate_id, default_embedding_provider_id)
    REFERENCES llm_provider_config(candidate_id, id)
);

ALTER TABLE agent_sessions
  ADD COLUMN llm_provider_name_snapshot VARCHAR(128);

ALTER TABLE agent_sessions
  ADD COLUMN llm_model_snapshot VARCHAR(128);

CREATE INDEX idx_agent_sessions_candidate_tenant_created
  ON agent_sessions (candidate_id, tenant_id, created_at DESC);
