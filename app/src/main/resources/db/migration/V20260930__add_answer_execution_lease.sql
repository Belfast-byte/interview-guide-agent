-- Execution leases fence concurrent/retried answer processing without replacing the answer fact.
ALTER TABLE agent_turns ADD COLUMN answer_execution_token VARCHAR(36);
ALTER TABLE agent_turns ADD COLUMN answer_lease_until TIMESTAMP;
ALTER TABLE agent_turns ADD COLUMN answer_error VARCHAR(500);
