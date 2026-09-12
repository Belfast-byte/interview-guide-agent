// @vitest-environment jsdom
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { AdaptiveInterviewSession } from '../../../types/adaptiveInterview';
import { draftKey, readDraft, useAnswerDraft } from './useAnswerDraft';

const session = { sessionId: 's1', currentTurn: 1, turns: [{ turnIndex: 1, questionType: 'CODE_REPAIR', answer: null,
  submittedCode: null, codeTask: { initialCode: 'class Initial {}', requirements: ['原子操作'], assumptions: ['共享库存'] } }],
} as AdaptiveInterviewSession;
beforeEach(() => sessionStorage.clear());
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('按用户、会话和轮次隔离草稿', async () => {
  sessionStorage.setItem(draftKey('other', 's1', 1), JSON.stringify({ code: 'secret', answer: 'secret' }));
  sessionStorage.setItem(draftKey('owner', 's2', 1), JSON.stringify({ code: 'different session', answer: '' }));
  sessionStorage.setItem(draftKey('owner', 's1', 2), JSON.stringify({ code: 'different turn', answer: '' }));
  const { result } = renderHook(() => useAnswerDraft({ owner: 'owner', session }));
  await waitFor(() => expect(result.current.draft.code).toBe('class Initial {}'));
  act(() => result.current.update({ code: 'class Repaired {}', answer: ' 说明 ' }));
  expect(readDraft(sessionStorage, draftKey('owner', 's1', 1))).toEqual({ code: 'class Repaired {}', answer: ' 说明 ' });
  expect(readDraft(sessionStorage, draftKey('other', 's1', 1))?.code).toBe('secret');
});
it('草稿 JSON 损坏时明确显示恢复失败', async () => {
  sessionStorage.setItem(draftKey('owner', 's1', 1), '{"code": 1}');
  const { result } = renderHook(() => useAnswerDraft({ owner: 'owner', session }));
  await waitFor(() => expect(result.current.error).toContain('草稿格式损坏'));
});
it('存储配额错误明确展示并保留内存中当前修改', async () => {
  const { result } = renderHook(() => useAnswerDraft({ owner: 'owner', session }));
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new DOMException('Quota exceeded', 'QuotaExceededError'); });
  act(() => result.current.update({ code: 'class Repaired {}' }));
  expect(result.current.error).toContain('草稿保存失败');
  expect(result.current.draft.code).toBe('class Repaired {}');
});
it('刷新进入下一轮时清理上一轮已接受草稿，保留新轮草稿', async () => {
  sessionStorage.setItem(draftKey('owner', 's1', 1), JSON.stringify({ code: 'old draft', answer: '' }));
  sessionStorage.setItem(draftKey('owner', 's1', 2), JSON.stringify({ code: 'next draft', answer: '' }));
  const next = { ...session, currentTurn: 2, turns: [
    { ...session.turns[0], submittedCode: 'accepted code' }, { ...session.turns[0], turnIndex: 2 },
  ] };
  const { result } = renderHook(() => useAnswerDraft({ owner: 'owner', session: next }));
  await waitFor(() => expect(result.current.draft.code).toBe('next draft'));
  expect(sessionStorage.getItem(draftKey('owner', 's1', 1))).toBeNull();
});

it('文字追问草稿只保存本轮文字，不复制前轮正式代码作为本轮草稿', async () => {
  const followup = { ...session, currentTurn: 2, turns: [
    { ...session.turns[0], codeTaskTurnIndex: 1, submittedCode: 'accepted code' },
    { ...session.turns[0], turnIndex: 2, codeTaskTurnIndex: 1, questionType: 'TEXT' as const },
  ] };
  const { result } = renderHook(() => useAnswerDraft({ owner: 'owner', session: followup }));
  expect(result.current.draft.code).toBe('');
  act(() => result.current.update({ answer: '文字解释' }));
  expect(readDraft(sessionStorage, draftKey('owner', 's1', 2))).toEqual({ code: '', answer: '文字解释' });
});
