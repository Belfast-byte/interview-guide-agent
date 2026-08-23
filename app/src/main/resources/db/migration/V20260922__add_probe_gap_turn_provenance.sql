ALTER TABLE agent_turns
  ADD COLUMN source_probe_gap_id BIGINT;

UPDATE agent_turns
SET source_probe_gap_id = (
  SELECT gap.id
  FROM agent_assessment_probe_gaps gap
  WHERE gap.assessment_id = agent_turns.source_assessment_id
  ORDER BY gap.gap_order ASC, gap.id ASC
  FETCH FIRST 1 ROW ONLY
)
WHERE trigger_type = 'ASSESSMENT_GAP';

ALTER TABLE agent_assessment_probe_gaps
  ADD CONSTRAINT uk_assessment_probe_gap_id_assessment
    UNIQUE (id, assessment_id);

ALTER TABLE agent_turns
  DROP CONSTRAINT agent_turn_trigger_check,
  ADD CONSTRAINT fk_agent_turn_source_probe_gap
    FOREIGN KEY (source_probe_gap_id, source_assessment_id)
    REFERENCES agent_assessment_probe_gaps(id, assessment_id),
  ADD CONSTRAINT uk_agent_turn_source_probe_gap UNIQUE (source_probe_gap_id),
  ADD CONSTRAINT agent_turn_trigger_check CHECK (
    (trigger_type = 'PLANNED'
      AND parent_turn_index IS NULL
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
