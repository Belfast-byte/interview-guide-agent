-- 辅助引用不再阻断 LLM 判题；没有已验证引用的缺口保留模型判断及其理由。
ALTER TABLE agent_assessment_probe_gaps ALTER COLUMN anchor DROP NOT NULL;
ALTER TABLE agent_assessment_probe_gaps DROP CONSTRAINT IF EXISTS agent_assessment_probe_gaps_closure_reason_check;
ALTER TABLE agent_assessment_probe_gaps ADD CONSTRAINT agent_assessment_probe_gaps_closure_reason_check
  CHECK (closure_reason IN ('BUDGET_EXHAUSTED', 'EVIDENCE_RESOLVED', 'MODEL_ASSESSED'));
