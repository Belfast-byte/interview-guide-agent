-- Legacy read-tool evidences have no lossless domain-fact mapping: read-only observations
-- are question provenance, not candidate capability evidence. Sandbox evidence remains.
DELETE FROM agent_evidences
WHERE tool_call_id IS NOT NULL;

ALTER TABLE agent_evidences
  DROP CONSTRAINT agent_evidence_source_check,
  DROP CONSTRAINT fk_agent_evidence_tool_call,
  DROP COLUMN tool_call_id;

ALTER TABLE agent_evidences
  ADD CONSTRAINT agent_evidence_source_check CHECK (
    (evidence_type = 'QUOTE'
      AND quote_text IS NOT NULL
      AND sandbox_execution_id IS NULL
      AND code_source_id IS NULL)
    OR
    (evidence_type = 'TOOL_RESULT'
      AND quote_text IS NULL
      AND sandbox_execution_id IS NOT NULL
      AND code_source_id IS NULL)
    OR
    (evidence_type = 'CODE_FACT'
      AND quote_text IS NULL
      AND sandbox_execution_id IS NULL
      AND code_source_id IS NOT NULL
      AND code_anchor IS NOT NULL
      AND code_fact_usage IN ('QUESTION_SOURCE', 'CLAIM_VERIFICATION'))
  );

ALTER TABLE agent_turns
  DROP CONSTRAINT agent_turn_trigger_check;

-- Preserve user-visible turns produced after an old read-tool result. Their durable meaning
-- is a model decision; the transient result event itself is intentionally not retained.
UPDATE agent_turns
SET trigger_type = 'AGENT_DECISION',
    source_tool_result_event_id = NULL
WHERE trigger_type = 'TOOL_RESULT';

ALTER TABLE agent_turns
  DROP CONSTRAINT fk_agent_turn_source_tool_result,
  DROP COLUMN source_tool_result_event_id;

ALTER TABLE agent_turns
  ADD CONSTRAINT agent_turn_trigger_check CHECK (
    (trigger_type = 'PLANNED'
      AND parent_turn_index IS NULL
      AND source_assessment_id IS NULL
      AND source_probe_gap_id IS NULL)
    OR
    (trigger_type = 'AGENT_DECISION'
      AND parent_turn_index IS NOT NULL
      AND source_assessment_id IS NULL
      AND source_probe_gap_id IS NULL)
    OR
    (trigger_type = 'ASSESSMENT_GAP'
      AND parent_turn_index IS NOT NULL
      AND source_assessment_id IS NOT NULL
      AND source_probe_gap_id IS NOT NULL)
  );

DROP TABLE agent_tool_result_events;
DROP TABLE agent_tool_calls;
DROP TABLE agent_action_intents;
DROP TABLE agent_work_state_patches;
DROP TABLE agent_work_states;

ALTER TABLE agent_plans
  DROP CONSTRAINT agent_plan_turns_check,
  DROP COLUMN suggested_tools,
  DROP COLUMN follow_up_budget,
  DROP COLUMN tool_budget;

ALTER TABLE agent_plans
  ADD CONSTRAINT agent_plan_turns_check CHECK (
    suggested_turns BETWEEN 1 AND 12
      AND allocated_turns BETWEEN 1 AND 12
  );
