ALTER TABLE agent_evidences
  ADD COLUMN sandbox_execution_id VARCHAR(36);

ALTER TABLE agent_evidences
  ADD CONSTRAINT fk_agent_evidence_sandbox_execution
  FOREIGN KEY (sandbox_execution_id) REFERENCES sandbox_executions(id);

ALTER TABLE agent_evidences
  DROP CONSTRAINT agent_evidence_source_check;

ALTER TABLE agent_evidences
  ADD CONSTRAINT agent_evidence_source_check
  CHECK (
    (evidence_type = 'QUOTE'
      AND quote_text IS NOT NULL
      AND tool_call_id IS NULL
      AND sandbox_execution_id IS NULL)
    OR
    (evidence_type = 'TOOL_RESULT'
      AND quote_text IS NULL
      AND ((tool_call_id IS NOT NULL AND sandbox_execution_id IS NULL)
        OR (tool_call_id IS NULL AND sandbox_execution_id IS NOT NULL)))
  );
