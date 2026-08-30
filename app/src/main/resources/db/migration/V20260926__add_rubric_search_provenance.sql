CREATE TABLE agent_rubric_index (
  question_id BIGINT PRIMARY KEY REFERENCES knowledge_base_questions(id) ON DELETE CASCADE,
  document_id UUID NOT NULL UNIQUE,
  source_updated_at TIMESTAMP(6) NOT NULL,
  indexed_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_agent_rubric_index_source_updated
  ON agent_rubric_index (source_updated_at);

ALTER TABLE agent_turns
  ADD COLUMN adopted_rubrics_json TEXT NOT NULL DEFAULT '[]';
