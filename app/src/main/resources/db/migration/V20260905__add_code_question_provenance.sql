ALTER TABLE agent_turns
  ADD COLUMN code_source_id VARCHAR(128),
  ADD COLUMN code_anchor VARCHAR(500),
  ADD COLUMN code_fact_usage VARCHAR(24),
  ADD CONSTRAINT agent_turn_code_provenance_check CHECK (
    (code_source_id IS NULL AND code_anchor IS NULL AND code_fact_usage IS NULL)
    OR
    (code_source_id IS NOT NULL AND code_anchor IS NOT NULL
      AND code_fact_usage IN ('QUESTION_SOURCE', 'CLAIM_VERIFICATION'))
  );
