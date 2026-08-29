ALTER TABLE sandbox_executions
  ADD COLUMN consumed_at TIMESTAMP(6);

ALTER TABLE agent_evidences
  ADD CONSTRAINT uk_agent_evidence_sandbox_execution
  UNIQUE (sandbox_execution_id);

CREATE INDEX idx_sandbox_executions_unconsumed_terminal
  ON sandbox_executions (finished_at)
  WHERE consumed_at IS NULL AND status IN ('DONE', 'TIMEOUT_QUEUED');
