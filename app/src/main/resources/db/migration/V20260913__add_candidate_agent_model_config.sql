CREATE TABLE candidate_agent_model_configs (
  candidate_id UUID PRIMARY KEY,
  base_url VARCHAR(512) NOT NULL,
  api_key_ciphertext VARCHAR(4096) NOT NULL,
  api_key_nonce VARCHAR(64) NOT NULL,
  model VARCHAR(128) NOT NULL,
  temperature DOUBLE PRECISION,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_candidate_agent_model_config_user
    FOREIGN KEY (candidate_id) REFERENCES users(id) ON DELETE CASCADE
);
