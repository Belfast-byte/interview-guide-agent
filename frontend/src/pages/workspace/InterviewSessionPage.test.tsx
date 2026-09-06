// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { adaptiveInterviewApi } from '../../api/adaptiveInterview';
import type { AdaptiveInterviewSession } from '../../types/adaptiveInterview';
import InterviewSessionPage from './InterviewSessionPage';

vi.mock('../../api/adaptiveInterview', () => ({ adaptiveInterviewApi: { get: vi.fn(), submitAnswerStream: vi.fn(), retryAnswerStream: vi.fn() } }));
const snapshot = (status: 'WAITING' | 'PROCESSING' | 'RETRYABLE' = 'RETRYABLE'): AdaptiveInterviewSession => ({
  sessionId: 'audit', runtimeVersion: 'v2', status: 'IN_PROGRESS', currentTurn: 1, maxTurns: 2,
  mode: 'PRACTICE', candidateLevel: 'EXPERIENCED', practiceScope: [], currentQuestion: '说明 HashMap 原理',
  failureReason: null, llmProviderName: null, llmModel: null, dimensions: [],
  turns: [{ turnIndex: 1, dimensionOrder: 0, question: '说明 HashMap 原理',
    answer: status === 'WAITING' ? null : '  原始回答  ', answerStatus: status, answerError: null }],
});
function mount() {
  render(<MemoryRouter initialEntries={['/session/audit']}><Routes>
    <Route path="/session/:sessionId" element={<InterviewSessionPage />} />
  </Routes></MemoryRouter>);
}
beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);

it('刷新后允许重试已保存的原答案，保留原文且不开放编辑', async () => {
  vi.mocked(adaptiveInterviewApi.get).mockResolvedValue(snapshot());
  vi.mocked(adaptiveInterviewApi.retryAnswerStream).mockImplementation(async (_id, _turnIndex, callbacks) => {
    callbacks.onDone(snapshot('PROCESSING'));
  });
  mount();
  fireEvent.click(await screen.findByRole('button', { name: '重试原答案' }));
  await waitFor(() => expect(adaptiveInterviewApi.retryAnswerStream).toHaveBeenCalledWith(
    'audit', 1, expect.anything(),
  ));
  expect(screen.queryByRole('textbox')).toBeNull();
  await screen.findByRole('button', { name: '回答处理中' });
});

it('连接中断后读取后台处理中状态，不把已保存答案回滚为可编辑', async () => {
  vi.mocked(adaptiveInterviewApi.get).mockResolvedValueOnce(snapshot('WAITING')).mockResolvedValue(snapshot('PROCESSING'));
  vi.mocked(adaptiveInterviewApi.submitAnswerStream).mockImplementation(async (_id, _payload, callbacks) => {
    callbacks.onError(new Error('连接中断'));
  });
  mount();
  fireEvent.change(await screen.findByRole('textbox'), { target: { value: '原始回答' } });
  fireEvent.click(screen.getByRole('button', { name: '提交回答' }));
  await screen.findByRole('button', { name: '回答处理中' });
  expect(adaptiveInterviewApi.get).toHaveBeenCalledTimes(2);
  expect(screen.queryByRole('textbox')).toBeNull();
});

it('处理中快照自动轮询并在执行失败后恢复重试入口', async () => {
  vi.mocked(adaptiveInterviewApi.get).mockResolvedValueOnce(snapshot('PROCESSING')).mockResolvedValue(snapshot());
  mount();
  expect((await screen.findByRole('button', { name: '回答处理中' }) as HTMLButtonElement).disabled).toBe(true);
  await screen.findByRole('button', { name: '重试原答案' }, { timeout: 3500 });
  expect(adaptiveInterviewApi.submitAnswerStream).not.toHaveBeenCalled();
});

it('断流时后台已推进到下一题，恢复后直接显示新题', async () => {
  const next = snapshot('WAITING');
  next.currentTurn = 2;
  next.turns = [
    { ...snapshot().turns[0], answerStatus: 'COMPLETED' },
    { turnIndex: 2, dimensionOrder: 0, question: '解释并发更新', answer: null, answerStatus: 'WAITING' },
  ];
  vi.mocked(adaptiveInterviewApi.get).mockResolvedValueOnce(snapshot('WAITING')).mockResolvedValue(next);
  vi.mocked(adaptiveInterviewApi.submitAnswerStream).mockImplementation(async (_id, _payload, callbacks) => {
    callbacks.onError(new Error('连接中断'));
  });
  mount();
  fireEvent.change(await screen.findByRole('textbox'), { target: { value: '原始回答' } });
  fireEvent.click(screen.getByRole('button', { name: '提交回答' }));
  await screen.findByText('解释并发更新');
  expect((screen.getByRole('textbox') as HTMLTextAreaElement).value).toBe('');
});

it('状态读取失败时阻止重提，刷新确认后恢复原答案重试', async () => {
  vi.mocked(adaptiveInterviewApi.get).mockResolvedValueOnce(snapshot('WAITING'))
    .mockRejectedValueOnce(new Error('网络不可用')).mockResolvedValue(snapshot());
  vi.mocked(adaptiveInterviewApi.submitAnswerStream).mockImplementation(async (_id, _payload, callbacks) => {
    callbacks.onError(new Error('连接中断'));
  });
  mount();
  fireEvent.change(await screen.findByRole('textbox'), { target: { value: '原始回答' } });
  fireEvent.click(screen.getByRole('button', { name: '提交回答' }));
  await screen.findByText(/暂时无法读取处理状态/);
  expect((screen.getByRole('textbox') as HTMLTextAreaElement).disabled).toBe(true);
  expect((screen.getByRole('button', { name: '提交回答' }) as HTMLButtonElement).disabled).toBe(true);
  fireEvent.click(screen.getByRole('button', { name: '刷新' }));
  expect((await screen.findByRole('button', { name: '重试原答案' }) as HTMLButtonElement).disabled).toBe(false);
});

it('轮询暂时失败后继续读取直到出现可重试状态', async () => {
  vi.mocked(adaptiveInterviewApi.get).mockResolvedValueOnce(snapshot('PROCESSING'))
    .mockRejectedValueOnce(new Error('暂时断网')).mockResolvedValue(snapshot());
  mount();
  await screen.findByRole('button', { name: '回答处理中' });
  await screen.findByRole('button', { name: '重试原答案' }, { timeout: 4800 });
  expect(adaptiveInterviewApi.get).toHaveBeenCalledTimes(3);
}, 6000);

it('断网后手动刷新发现后台已推进时清空旧题草稿', async () => {
  const next = snapshot('WAITING');
  next.currentTurn = 2;
  next.turns = [
    { ...snapshot().turns[0], answerStatus: 'COMPLETED' },
    { turnIndex: 2, dimensionOrder: 0, question: '解释并发更新', answer: null, answerStatus: 'WAITING' },
  ];
  vi.mocked(adaptiveInterviewApi.get).mockResolvedValueOnce(snapshot('WAITING'))
    .mockRejectedValueOnce(new Error('网络不可用')).mockResolvedValue(next);
  vi.mocked(adaptiveInterviewApi.submitAnswerStream).mockImplementation(async (_id, _payload, callbacks) => {
    callbacks.onError(new Error('连接中断'));
  });
  mount();
  fireEvent.change(await screen.findByRole('textbox'), { target: { value: '旧题草稿' } });
  fireEvent.click(screen.getByRole('button', { name: '提交回答' }));
  await screen.findByText(/暂时无法读取处理状态/);
  fireEvent.click(screen.getByRole('button', { name: '刷新' }));
  await screen.findByText('解释并发更新');
  expect((screen.getByRole('textbox') as HTMLTextAreaElement).value).toBe('');
  expect((screen.getByRole('textbox') as HTMLTextAreaElement).disabled).toBe(false);
});
