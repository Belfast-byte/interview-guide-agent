import assert from 'node:assert/strict';
import test from 'node:test';
import { ADAPTIVE_INTERVIEW_TEST_SAMPLE } from './adaptiveInterviewTestSample.ts';

test('自适应面试测试样例包含可直接提交的 JD 和简历', () => {
  assert.match(ADAPTIVE_INTERVIEW_TEST_SAMPLE.jd, /Java 后端开发工程师/);
  assert.match(ADAPTIVE_INTERVIEW_TEST_SAMPLE.resume, /项目经历/);
  assert.ok(ADAPTIVE_INTERVIEW_TEST_SAMPLE.jd.trim().length > 0);
  assert.ok(ADAPTIVE_INTERVIEW_TEST_SAMPLE.resume.trim().length > 0);
});
