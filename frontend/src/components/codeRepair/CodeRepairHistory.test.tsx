// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it } from 'vitest';
import { EditorView } from '@codemirror/view';
import CodeRepairHistory from './CodeRepairHistory';

const task = { initialCode: 'void broken() {}', requirements: ['保持原子性'], assumptions: ['共享库存'] };
beforeEach(() => {
  Range.prototype.getClientRects = () => [] as unknown as DOMRectList;
  Range.prototype.getBoundingClientRect = () => new DOMRect();
});
afterEach(cleanup);

it('文字追问没有本轮代码提交时，初始代码明确作为题目材料', () => {
  render(<CodeRepairHistory turn={{ turnIndex: 2, questionType: 'TEXT', codeTaskTurnIndex: 1,
    codeTask: task, submittedCode: null, answer: '这是文字解释' }} />);
  expect(screen.getByLabelText('原始代码（题目材料）')).toBeTruthy();
  expect(screen.getByText('本轮未提交代码。')).toBeTruthy();
  expect(screen.queryByRole('tab', { name: /提交代码/ })).toBeNull();
  expect(screen.queryByRole('tab', { name: '差异' })).toBeNull();
  expect(EditorView.findFromDOM(screen.getByRole('textbox', { name: 'Java 代码（只读）' }))!.state.doc.toString()).toBe(task.initialCode);
});
it('只有正式提交存在才展示带来源轮次的提交与差异', () => {
  const submittedCode = 'void fixed() {}';
  render(<CodeRepairHistory turn={{ turnIndex: 1, questionType: 'CODE_REPAIR', codeTask: task, submittedCode }} />);
  expect(screen.getByRole('tab', { name: '第 1 轮提交代码' })).toBeTruthy();
  expect(screen.queryByText('本轮未提交代码。')).toBeNull();
  expect(EditorView.findFromDOM(screen.getByRole('textbox', { name: 'Java 代码（只读）' }))!.state.doc.toString()).toBe(submittedCode);
  fireEvent.click(screen.getByRole('tab', { name: '差异' }));
  expect(screen.getByText('左：原始代码 · 右：第 1 轮提交代码')).toBeTruthy();
});
it('Episode 前文只含正式代码时仍标明来源轮次', () => {
  render(<CodeRepairHistory turn={{ turnIndex: 1, submittedCode: 'void fixed() {}' }} />);
  expect(screen.getByText('第 1 轮提交代码')).toBeTruthy();
  expect(screen.queryByText(/原始代码/)).toBeNull();
});
