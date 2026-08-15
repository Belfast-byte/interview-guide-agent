ALTER TABLE sandbox_executions
  ADD COLUMN policy_violation VARCHAR(32);

ALTER TABLE sandbox_executions
  ADD CONSTRAINT sandbox_execution_policy_violation_check
  CHECK (policy_violation IS NULL OR policy_violation IN (
    'NETWORK_ACCESS',
    'FILESYSTEM_ACCESS',
    'PROCESS_LIMIT',
    'OUTPUT_LIMIT'
  ));
