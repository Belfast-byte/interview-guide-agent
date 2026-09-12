-- V20261007 incremental contract fixture: relevant columns of the preceding schema.
-- This is a real PostgreSQL check, not the complete historical Flyway migration chain.
CREATE TABLE agent_turns (
    session_id VARCHAR(36) NOT NULL,
    turn_index INTEGER NOT NULL,
    answer TEXT,
    answer_execution_token VARCHAR(36),
    response_type VARCHAR(24),
    sandbox_execution_id VARCHAR(36),
    UNIQUE (session_id, turn_index)
);
CREATE TABLE agent_assessments (id BIGINT PRIMARY KEY);
CREATE TABLE agent_evidences (id BIGINT PRIMARY KEY, quote_text TEXT);
CREATE TABLE agent_assessment_probe_gaps (id BIGINT PRIMARY KEY, anchor TEXT, closure_evidence_quote TEXT);

INSERT INTO agent_turns VALUES ('legacy-text', 1, '历史文字原文', NULL, 'ASK', NULL);
INSERT INTO agent_turns VALUES ('legacy-sandbox', 1, '旧执行说明', 'old-token', 'ASK', 'old-execution');
INSERT INTO agent_assessments VALUES (1);
INSERT INTO agent_evidences VALUES (1, '历史原句');
INSERT INTO agent_assessment_probe_gaps VALUES (1, '旧锚点', '旧关闭证据');
CREATE TEMP TABLE historical_turns AS SELECT * FROM agent_turns;

\ir ../../app/src/main/resources/db/migration/V20261007__add_java_code_repair_facts.sql

CREATE FUNCTION assert_true(condition BOOLEAN, label TEXT) RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    IF condition IS DISTINCT FROM TRUE THEN RAISE EXCEPTION 'FAILED: %', label; END IF;
    RAISE NOTICE 'PASS: %', label;
END;
$$;
CREATE FUNCTION expect_rejection(statement TEXT, expected_state TEXT, label TEXT)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE actual_state TEXT;
BEGIN
    BEGIN
        EXECUTE statement;
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS actual_state = RETURNED_SQLSTATE;
    END;
    PERFORM assert_true(actual_state = expected_state, label || ' (SQLSTATE ' || COALESCE(actual_state, 'no error') || ')');
END;
$$;

SELECT assert_true(NOT EXISTS (
    (SELECT session_id, turn_index, answer, answer_execution_token, response_type, sandbox_execution_id FROM agent_turns
     EXCEPT SELECT * FROM historical_turns)
    UNION ALL
    (SELECT * FROM historical_turns EXCEPT
     SELECT session_id, turn_index, answer, answer_execution_token, response_type, sandbox_execution_id FROM agent_turns)
), 'legacy text and sandbox columns preserve exact values');
SELECT assert_true((SELECT bool_and(question_type = 'TEXT' AND code_task_json IS NULL
    AND code_task_turn_index IS NULL AND submitted_code IS NULL) FROM agent_turns), 'legacy rows remain TEXT without invented code');
SELECT assert_true((SELECT code_review_json IS NULL FROM agent_assessments WHERE id = 1), 'legacy review stays null');
SELECT assert_true((SELECT quote_locator_json IS NULL AND quote_text = '历史原句'
    FROM agent_evidences WHERE id = 1), 'legacy evidence has no fabricated locator');
SELECT assert_true((SELECT anchor_locator_json IS NULL AND closure_evidence_locator_json IS NULL
    AND anchor = '旧锚点' AND closure_evidence_quote = '旧关闭证据'
    FROM agent_assessment_probe_gaps WHERE id = 1), 'legacy gap locators stay null');

INSERT INTO agent_turns(session_id, turn_index, question_type, code_task_json, code_task_turn_index)
VALUES ('practice', 1, 'CODE_REPAIR', '{"initialCode":"initial\n","requirements":["reserve atomically"],"assumptions":["shared stock"],"reviewGuide":{"checks":[{"id":"C1","defect":"race","trigger":"concurrency","acceptance":"atomic reservation"}]}}', 1);
SELECT assert_true((SELECT code_task_turn_index = turn_index FROM agent_turns
    WHERE session_id = 'practice' AND turn_index = 1), 'root self-reference is valid in the inserting statement');
UPDATE agent_turns SET submitted_code = E'  reserve();\r\n', answer_execution_token = 'token'
WHERE session_id = 'practice' AND turn_index = 1;
SELECT assert_true((SELECT answer IS NULL AND submitted_code = E'  reserve();\r\n'
    FROM agent_turns WHERE session_id = 'practice' AND turn_index = 1), 'accepted code-only answer preserves whitespace');
INSERT INTO agent_turns(session_id, turn_index, question_type, code_task_turn_index)
VALUES ('practice', 2, 'CODE_REPAIR', 1), ('practice', 3, 'TEXT', 1);
SELECT assert_true((SELECT count(*) = 3 FROM agent_turns WHERE session_id = 'practice'), 'same-session revision and TEXT follow-up accepted');

SELECT expect_rejection($s$INSERT INTO agent_turns(session_id,turn_index,question_type,code_task_turn_index)
    VALUES ('other',2,'CODE_REPAIR',1)$s$, '23503', 'cannot reference root present only in another session');
SELECT expect_rejection($s$INSERT INTO agent_turns(session_id,turn_index,question_type)
    VALUES ('bad-type',1,'UNKNOWN')$s$, '23514', 'unknown question type rejected');
SELECT expect_rejection($s$INSERT INTO agent_turns(session_id,turn_index,question_type)
    VALUES ('missing-root',1,'CODE_REPAIR')$s$, '23514', 'code question requires root');
SELECT expect_rejection($s$INSERT INTO agent_turns(session_id,turn_index,submitted_code)
    VALUES ('text-code',1,'reserve();')$s$, '23514', 'TEXT cannot contain submitted code');
SELECT expect_rejection($s$INSERT INTO agent_turns(session_id,turn_index,code_task_json,code_task_turn_index)
    VALUES ('text-task',1,'{}',1)$s$, '23514', 'TEXT cannot own a code task');
SELECT expect_rejection($s$INSERT INTO agent_turns(session_id,turn_index,question_type,code_task_json,code_task_turn_index)
    VALUES ('practice',4,'CODE_REPAIR','{}',1)$s$, '23514', 'revision cannot duplicate task JSON');
SELECT expect_rejection($s$INSERT INTO agent_turns(session_id,turn_index,question_type,code_task_turn_index)
    VALUES ('self-without-task',1,'CODE_REPAIR',1)$s$, '23514', 'self-reference requires root task');
SELECT expect_rejection($s$UPDATE agent_turns SET submitted_code=E' \t\r\n'
    WHERE session_id='practice' AND turn_index=1$s$, '23514', 'blank code rejected');
SELECT expect_rejection($s$UPDATE agent_turns SET answer='explanation only'
    WHERE session_id='practice' AND turn_index=2$s$, '23514', 'explanation without accepted code rejected');
SELECT expect_rejection($s$UPDATE agent_turns SET answer_execution_token='token'
    WHERE session_id='practice' AND turn_index=2$s$, '23514', 'claimed code answer must contain code');
SELECT expect_rejection($s$UPDATE agent_turns SET response_type='ASK'
    WHERE session_id='practice' AND turn_index=2$s$, '23514', 'completed code answer must contain code');

-- Root-vs-reference and semantic JSON checks intentionally belong to the existing
-- application write boundary; the composite FK proves same-session existence only.
SELECT 'V20261007 PostgreSQL incremental constraints verified' AS result;
