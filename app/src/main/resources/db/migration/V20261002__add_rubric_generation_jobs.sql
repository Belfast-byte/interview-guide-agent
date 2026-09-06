CREATE TABLE agent_rubric_generation_jobs (
  id VARCHAR(255) PRIMARY KEY,
  session_id VARCHAR(36) NOT NULL,
  turn_index INTEGER NOT NULL,
  dimension TEXT NOT NULL,
  focus TEXT NOT NULL,
  question TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  attempts INTEGER NOT NULL,
  lease_token VARCHAR(36),
  available_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  question_id BIGINT REFERENCES knowledge_base_questions(id) ON DELETE SET NULL,
  rubric_version VARCHAR(64),
  draft_json TEXT,
  review_json TEXT,
  manual_reviews_json TEXT,
  generator_provider VARCHAR(120),
  judge_provider VARCHAR(120),
  last_error VARCHAR(500),
  UNIQUE(session_id, turn_index)
);
CREATE INDEX idx_rubric_generation_pending ON agent_rubric_generation_jobs(status, available_at);
