ALTER TABLE agent_turns
  ADD COLUMN working_memory_snapshot TEXT;

ALTER TABLE agent_turns
  DROP CONSTRAINT IF EXISTS uk_agent_turn_source_probe_gap;
