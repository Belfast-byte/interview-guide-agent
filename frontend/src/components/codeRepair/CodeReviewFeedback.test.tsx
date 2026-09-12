// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import CodeReviewFeedback, { EvidenceSource } from './CodeReviewFeedback';

afterEach(cleanup);
it('重复代码证据按 UTF-16 偏移定位到正式提交，保留空白与换行', () => {
  const code = '// 库存😀\nreserve();\nreserve();';
  const startOffset = code.lastIndexOf('reserve();');
  render(<CodeReviewFeedback code={code} answer="说明不含代码"
    review={{ checks: [{ checkId: 'C1', result: 'UNDETERMINED', reason: '接口的事务范围尚未明确' }] }}
    feedback={{ depthLevel: 'L2', rationale: '需说明事务边界', evidenceQuotes: [
      { source: 'SUBMITTED_CODE', quote: 'reserve();', startOffset },
    ] }} />);
  fireEvent.click(screen.getByRole('button', { name: /定位证据/ }));
  expect(screen.getByText('正式原文 · 第 3 行')).toBeTruthy();
  const source = screen.getByLabelText('证据原文定位');
  expect(source.textContent).toBe(code);
  expect(source.querySelector('mark')?.previousSibling?.textContent).toBe('// 库存😀\nreserve();\n');
  expect(screen.getByText('C1 · 无法确定')).toBeTruthy();
});
it('文字证据定位到修改说明，异常偏移明确报错', () => {
  render(<EvidenceSource source="原子扣减" quote={{ source: 'ANSWER_TEXT', quote: '不存在', startOffset: 0 }} />);
  expect(screen.getByRole('alert').textContent).toContain('证据位置与正式原文不一致');
  expect(screen.queryByLabelText('证据原文定位')).toBeNull();
});
