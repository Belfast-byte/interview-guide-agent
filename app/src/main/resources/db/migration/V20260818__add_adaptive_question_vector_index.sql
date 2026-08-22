CREATE TABLE agent_question_index (
  question_id BIGINT PRIMARY KEY,
  document_id UUID NOT NULL UNIQUE,
  source_updated_at TIMESTAMP(6) NOT NULL,
  indexed_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_agent_question_index_source_updated
  ON agent_question_index (source_updated_at);
