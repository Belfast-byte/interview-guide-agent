ALTER TABLE agent_assessment_probe_gaps
  ADD COLUMN closed_by_assessment_id BIGINT,
  ADD COLUMN closure_reason VARCHAR(32);

ALTER TABLE agent_assessment_probe_gaps
  ADD CONSTRAINT fk_probe_gap_closed_by_assessment
    FOREIGN KEY (closed_by_assessment_id)
    REFERENCES agent_assessments(id);

ALTER TABLE agent_assessments
  ADD COLUMN budget_exhausted_final BOOLEAN NOT NULL DEFAULT FALSE;
