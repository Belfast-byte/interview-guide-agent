ALTER TABLE agent_evidences
  DROP CONSTRAINT agent_evidence_type_check;

ALTER TABLE agent_evidences
  ADD CONSTRAINT agent_evidence_type_check
  CHECK (evidence_type IN ('QUOTE', 'TOOL_RESULT', 'CODE_FACT'));
