ALTER TABLE agent_turns
  DROP CONSTRAINT agent_turn_trigger_check;

ALTER TABLE agent_turns
  ADD CONSTRAINT agent_turn_trigger_check CHECK (
    (trigger_type = 'PLANNED'
      AND parent_turn_index IS NULL
      AND source_assessment_id IS NULL
      AND source_probe_gap_id IS NULL
      AND source_tool_result_event_id IS NULL)
    OR
    (trigger_type = 'AGENT_DECISION'
      AND parent_turn_index IS NOT NULL
      AND source_assessment_id IS NULL
      AND source_probe_gap_id IS NULL
      AND source_tool_result_event_id IS NULL)
    OR
    (trigger_type = 'ASSESSMENT_GAP'
      AND parent_turn_index IS NOT NULL
      AND source_assessment_id IS NOT NULL
      AND source_probe_gap_id IS NOT NULL
      AND source_tool_result_event_id IS NULL)
    OR
    (trigger_type = 'TOOL_RESULT'
      AND parent_turn_index IS NOT NULL
      AND source_assessment_id IS NULL
      AND source_probe_gap_id IS NULL
      AND source_tool_result_event_id IS NOT NULL)
  );
