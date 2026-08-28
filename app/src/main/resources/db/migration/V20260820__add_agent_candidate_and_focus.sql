ALTER TABLE agent_sessions
  ADD COLUMN candidate_id VARCHAR(64) NOT NULL;

ALTER TABLE agent_plans
  ADD COLUMN focus_id VARCHAR(64) NOT NULL;
