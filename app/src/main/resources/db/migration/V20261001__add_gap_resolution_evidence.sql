ALTER TABLE agent_assessment_probe_gaps ADD COLUMN closure_evidence_quote TEXT;
ALTER TABLE agent_assessment_probe_gaps ADD COLUMN closure_summary VARCHAR(500);
ALTER TABLE agent_assessment_probe_gaps DROP CONSTRAINT IF EXISTS agent_assessment_probe_gaps_closure_reason_check;
ALTER TABLE agent_assessment_probe_gaps ADD CONSTRAINT agent_assessment_probe_gaps_closure_reason_check CHECK (closure_reason IN ('BUDGET_EXHAUSTED', 'EVIDENCE_RESOLVED'));
