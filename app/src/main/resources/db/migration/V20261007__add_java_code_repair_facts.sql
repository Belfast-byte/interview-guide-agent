-- 旧题保留文字输入协议；旧沙箱字段和历史答案不转换。
ALTER TABLE agent_turns
    ADD COLUMN question_type VARCHAR(24) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN code_task_json JSONB,
    ADD COLUMN code_task_turn_index INTEGER,
    ADD COLUMN submitted_code TEXT;

ALTER TABLE agent_assessments ADD COLUMN code_review_json JSONB;
ALTER TABLE agent_evidences ADD COLUMN quote_locator_json JSONB;
ALTER TABLE agent_assessment_probe_gaps
    ADD COLUMN anchor_locator_json JSONB,
    ADD COLUMN closure_evidence_locator_json JSONB;

ALTER TABLE agent_turns
    ADD CONSTRAINT fk_agent_turn_code_task
        FOREIGN KEY (session_id, code_task_turn_index)
        REFERENCES agent_turns (session_id, turn_index),
    ADD CONSTRAINT ck_agent_turn_question_type
        CHECK (question_type IN ('TEXT', 'CODE_REPAIR')),
    ADD CONSTRAINT ck_agent_turn_code_task_shape CHECK (
        (question_type = 'TEXT' AND code_task_json IS NULL AND submitted_code IS NULL)
        OR (question_type = 'CODE_REPAIR' AND code_task_turn_index IS NOT NULL)
    ),
    ADD CONSTRAINT ck_agent_turn_code_task_root CHECK (
        (code_task_json IS NOT NULL AND question_type = 'CODE_REPAIR' AND code_task_turn_index = turn_index)
        OR (code_task_json IS NULL AND (code_task_turn_index IS NULL OR code_task_turn_index < turn_index))
    ),
    ADD CONSTRAINT ck_agent_turn_code_answer CHECK (
        question_type <> 'CODE_REPAIR'
        OR (submitted_code IS NULL AND answer IS NULL AND answer_execution_token IS NULL AND response_type IS NULL)
        OR (submitted_code IS NOT NULL AND submitted_code ~ '[^[:space:]]')
    );
