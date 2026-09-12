// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { EditorView } from '@codemirror/view';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { adaptiveInterviewApi } from '../../api/adaptiveInterview';
import type { AdaptiveInterviewSession } from '../../types/adaptiveInterview';
import InterviewSessionPage from './InterviewSessionPage';
import { draftKey } from './interview/useAnswerDraft';

vi.mock('../../auth/AuthContext', () => ({ useAuth: () => ({ user: { candidateId: 'candidate' } }) }));
vi.mock('../../api/adaptiveInterview', () => ({ adaptiveInterviewApi: { get: vi.fn(), submitAnswerStream: vi.fn(), retryAnswerStream: vi.fn() } }));
const original = 'void reserve() {\n  stocks.set(0);\n}';
const repaired = 'void reserve() {\n  stocks.tryReserve();\n}';
function snapshot(): AdaptiveInterviewSession {
  return { sessionId: 'repair', runtimeVersion: 'v2', status: 'IN_PROGRESS', currentTurn: 1, maxTurns: 4,
    mode: 'PRACTICE', candidateLevel: 'EXPERIENCED', practiceScope: [], currentQuestion: '修复库存扣减',
    failureReason: null, llmProviderName: null, llmModel: null, dimensions: [],
    turns: [{ turnIndex: 1, dimensionOrder: 0, question: '修复库存扣减', answer: null, answerStatus: 'WAITING',
      questionType: 'CODE_REPAIR', codeTaskTurnIndex: 1,
      codeTask: { initialCode: original, requirements: ['不能超卖'], assumptions: ['数据库共享'] }, submittedCode: null }],
  };
}
function mount() {
  return render(<MemoryRouter initialEntries={['/session/repair']}><Routes>
    <Route path="/session/:sessionId" element={<InterviewSessionPage />} />
  </Routes></MemoryRouter>);
}
async function edit(code: string) {
  const content = await screen.findByRole('textbox', { name: 'Java 代码' });
  const view = EditorView.findFromDOM(content)!;
  act(() => view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: code } }));
}
beforeEach(() => {
  vi.resetAllMocks(); sessionStorage.clear();
  Range.prototype.getClientRects = () => [] as unknown as DOMRectList;
  Range.prototype.getBoundingClientRect = () => new DOMRect();
});
afterEach(cleanup);

it('真实 Java 编辑器提交只有代码的答案，并清理已接受草稿', async () => {
  vi.mocked(adaptiveInterviewApi.get).mockResolvedValue(snapshot());
  vi.mocked(adaptiveInterviewApi.submitAnswerStream).mockImplementation(async (_id, payload, callbacks) => {
    const accepted = snapshot();
    callbacks.onDone({ ...accepted, turns: [{ ...accepted.turns[0], submittedCode: payload.codeRepair!.code, answerStatus: 'PROCESSING' }] });
  });
  mount(); await edit(repaired);
  expect(screen.queryByRole('button', { name: /运行|编译|测试/ })).toBeNull();
  expect(screen.getByRole('textbox', { name: 'Java 代码' }).querySelector('.cm-line span')).toBeTruthy();
  expect(JSON.parse(sessionStorage.getItem(draftKey('candidate', 'repair', 1))!).code).toBe(repaired);
  fireEvent.click(screen.getByRole('button', { name: '提交修改' }));
  await screen.findByRole('button', { name: '回答处理中' });
  expect(adaptiveInterviewApi.submitAnswerStream).toHaveBeenCalledWith('repair',
    { turnIndex: 1, answer: null, codeRepair: { code: repaired } }, expect.anything());
  expect(sessionStorage.getItem(draftKey('candidate', 'repair', 1))).toBeNull();
  expect(screen.queryByRole('textbox', { name: 'Java 代码' })).toBeNull();
});

it('切换真实 merge 差异视图及刷新恢复代码和未裁剪说明', async () => {
  vi.mocked(adaptiveInterviewApi.get).mockResolvedValue(snapshot());
  const mounted = mount(); await edit(repaired);
  fireEvent.change(screen.getByLabelText('修改说明（可选）'), { target: { value: '  原子扣减\n' } });
  fireEvent.click(screen.getByRole('tab', { name: '差异' }));
  expect(screen.getByLabelText('原始代码与当前修改的差异').querySelector('.cm-mergeView')).toBeTruthy();
  fireEvent.click(screen.getByRole('tab', { name: '当前修改' }));
  expect(EditorView.findFromDOM(screen.getByRole('textbox', { name: 'Java 代码' }))!.state.doc.toString()).toBe(repaired);
  mounted.unmount(); mount();
  await waitFor(() => expect(EditorView.findFromDOM(screen.getByRole('textbox', { name: 'Java 代码' }))!.state.doc.toString()).toBe(repaired));
  expect((screen.getByLabelText('修改说明（可选）') as HTMLTextAreaElement).value).toBe('  原子扣减\n');
});

it('失败保留草稿，刷新后的服务端正式代码优先于旧草稿', async () => {
  vi.mocked(adaptiveInterviewApi.get).mockResolvedValue(snapshot());
  vi.mocked(adaptiveInterviewApi.submitAnswerStream).mockRejectedValue(new Error('模型暂不可用'));
  const mounted = mount(); await edit(repaired);
  fireEvent.click(screen.getByRole('button', { name: '提交修改' }));
  await screen.findByText('模型暂不可用');
  expect(JSON.parse(sessionStorage.getItem(draftKey('candidate', 'repair', 1))!).code).toBe(repaired);
  mounted.unmount();
  const accepted = snapshot();
  vi.mocked(adaptiveInterviewApi.get).mockResolvedValue({ ...accepted,
    turns: [{ ...accepted.turns[0], submittedCode: 'void accepted() {}', answerStatus: 'RETRYABLE' }] });
  mount(); await screen.findByRole('button', { name: '重试原答案' });
  expect(screen.queryByRole('textbox', { name: 'Java 代码' })).toBeNull();
  expect(screen.getByRole('textbox', { name: 'Java 代码（只读）' }).textContent).toContain('accepted');
  expect(sessionStorage.getItem(draftKey('candidate', 'repair', 1))).toBeNull();
});

it('练习修订从同任务最近提交开始，差异基线保留原始代码', async () => {
  const initial = snapshot();
  vi.mocked(adaptiveInterviewApi.get).mockResolvedValue({ ...initial, currentTurn: 2, turns: [
    { ...initial.turns[0], submittedCode: repaired, answerStatus: 'COMPLETED',
      codeReview: { checks: [{ checkId: 'C1', result: 'NOT_SATISFIED', reason: '仍需说明库存不足语义' }] } },
    { ...initial.turns[0], turnIndex: 2, question: '继续修复不足' },
  ] });
  mount(); await screen.findByText('继续修复不足');
  expect(screen.getByText('上次提交的审阅反馈 · 第 1 轮')).toBeTruthy();
  expect(screen.getByText('仍需说明库存不足语义')).toBeTruthy();
  await waitFor(() => expect(EditorView.findFromDOM(screen.getByRole('textbox', { name: 'Java 代码' }))!.state.doc.toString()).toBe(repaired));
  fireEvent.click(screen.getByRole('tab', { name: '原始代码' }));
  expect(EditorView.findFromDOM(screen.getByRole('textbox', { name: 'Java 代码（只读）' }))!.state.doc.toString()).toBe(original);
});

it('评估追问代码只读、仅提交文字且不公开审阅反馈', async () => {
  const initial = snapshot();
  vi.mocked(adaptiveInterviewApi.get).mockResolvedValue({ ...initial, mode: 'EVALUATION', currentTurn: 2, turns: [
    { ...initial.turns[0], submittedCode: repaired, answerStatus: 'COMPLETED' },
    { ...initial.turns[0], turnIndex: 2, question: '解释原子性的范围', questionType: 'TEXT' },
  ] });
  mount(); await screen.findByText('解释原子性的范围');
  expect(screen.getByRole('tab', { name: '第 1 轮提交代码' })).toBeTruthy();
  expect(screen.queryByRole('tab', { name: '本轮提交代码' })).toBeNull();
  expect(screen.queryByRole('textbox', { name: 'Java 代码' })).toBeNull();
  expect(screen.queryByLabelText('模型代码审阅反馈')).toBeNull();
  fireEvent.change(screen.getByLabelText('你的回答'), { target: { value: '数据库事务' } });
  fireEvent.click(screen.getByRole('button', { name: '提交回答' }));
  await waitFor(() => expect(adaptiveInterviewApi.submitAnswerStream).toHaveBeenCalledWith('repair',
    { turnIndex: 2, answer: '数据库事务' }, expect.anything()));
});

it('状态恢复失败只读锁定的草稿仍标为当前修改，不冒充正式提交', async () => {
  vi.mocked(adaptiveInterviewApi.get).mockResolvedValueOnce(snapshot()).mockRejectedValue(new Error('状态不可用'));
  vi.mocked(adaptiveInterviewApi.submitAnswerStream).mockRejectedValue(new Error('连接中断'));
  mount(); await edit(repaired);
  fireEvent.click(screen.getByRole('button', { name: '提交修改' }));
  await screen.findByText(/暂时无法读取处理状态/);
  expect(screen.getByRole('tab', { name: '当前修改' })).toBeTruthy();
  expect(screen.queryByRole('tab', { name: /提交代码/ })).toBeNull();
  expect(EditorView.findFromDOM(screen.getByRole('textbox', { name: 'Java 代码（只读）' }))!.state.doc.toString()).toBe(repaired);
  expect(JSON.parse(sessionStorage.getItem(draftKey('candidate', 'repair', 1))!).code).toBe(repaired);
});
