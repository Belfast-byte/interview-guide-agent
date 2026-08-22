ALTER TABLE agent_evidences
  ADD COLUMN code_source_id VARCHAR(128),
  ADD COLUMN code_anchor VARCHAR(500),
  ADD COLUMN code_fact_usage VARCHAR(24);

ALTER TABLE agent_evidences
  DROP CONSTRAINT agent_evidence_source_check;

ALTER TABLE agent_evidences
  ADD CONSTRAINT agent_evidence_source_check
  CHECK (
    (evidence_type = 'QUOTE'
      AND quote_text IS NOT NULL
      AND tool_call_id IS NULL
      AND sandbox_execution_id IS NULL
      AND code_source_id IS NULL)
    OR
    (evidence_type = 'TOOL_RESULT'
      AND quote_text IS NULL
      AND code_source_id IS NULL
      AND ((tool_call_id IS NOT NULL AND sandbox_execution_id IS NULL)
        OR (tool_call_id IS NULL AND sandbox_execution_id IS NOT NULL)))
    OR
    (evidence_type = 'CODE_FACT'
      AND quote_text IS NULL
      AND tool_call_id IS NULL
      AND sandbox_execution_id IS NULL
      AND code_source_id IS NOT NULL
      AND code_anchor IS NOT NULL
      AND code_fact_usage IN ('QUESTION_SOURCE', 'CLAIM_VERIFICATION'))
  );
