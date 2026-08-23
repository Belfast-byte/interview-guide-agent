import assert from 'node:assert/strict';
import test from 'node:test';
import { extractPartialContent } from './adaptiveInterviewStream.ts';

test('content 第一个字符到达时立即可提取', () => {
  assert.equal(extractPartialContent('{"type":"ASK","content":"首'), '首');
});

test('累积增量保留转义字符并在 content 结束处停止', () => {
  assert.equal(
    extractPartialContent('{"content":"第一行\\n第二行","reason":"继续'),
    '第一行\n第二行',
  );
});

test('结构化重试开始后读取最新一次 content', () => {
  const raw = '{"type":"ASK","content":"","reason":"invalid"}'
    + '{"type":"ASK","content":"新题';

  assert.equal(extractPartialContent(raw), '新题');
});
