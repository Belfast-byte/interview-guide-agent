ALTER TABLE sandbox_executions
  ADD COLUMN workload_type VARCHAR(16) NOT NULL DEFAULT 'ALGORITHM';

ALTER TABLE sandbox_executions
  ADD COLUMN scenario_id VARCHAR(64);

ALTER TABLE sandbox_executions
  ADD COLUMN workspace_ref VARCHAR(512);

ALTER TABLE sandbox_executions
  ADD COLUMN tests_ref VARCHAR(512);

ALTER TABLE sandbox_executions
  ALTER COLUMN problem_id DROP NOT NULL;

ALTER TABLE sandbox_executions
  ALTER COLUMN workload_type DROP DEFAULT;

ALTER TABLE sandbox_executions
  ADD CONSTRAINT sandbox_execution_workload_check
    CHECK (workload_type IN ('ALGORITHM', 'PATCH'));

ALTER TABLE sandbox_executions
  ADD CONSTRAINT sandbox_execution_reference_check CHECK (
    (workload_type = 'ALGORITHM'
      AND problem_id IS NOT NULL
      AND scenario_id IS NULL
      AND workspace_ref IS NULL
      AND tests_ref IS NULL)
    OR
    (workload_type = 'PATCH'
      AND problem_id IS NULL
      AND scenario_id IS NOT NULL
      AND workspace_ref IS NOT NULL
      AND tests_ref IS NOT NULL)
  );

CREATE INDEX idx_sandbox_executions_scenario
  ON sandbox_executions (session_id, scenario_id, created_at);
