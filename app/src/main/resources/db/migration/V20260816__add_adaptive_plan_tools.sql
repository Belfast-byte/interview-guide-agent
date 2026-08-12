ALTER TABLE agent_plans
  ADD COLUMN suggested_tools VARCHAR(500) NOT NULL DEFAULT '';

ALTER TABLE agent_plans
  ALTER COLUMN suggested_tools DROP DEFAULT;

ALTER TABLE agent_plans
  ADD COLUMN suggested_skill VARCHAR(64);
