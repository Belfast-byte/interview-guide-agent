import { useEffect, useState } from 'react';
import type { AdaptiveInterviewSession, AdaptiveInterviewTurn } from '../../../types/adaptiveInterview';

export const hasAnswer = (turn: AdaptiveInterviewTurn) => turn.answer != null || turn.submittedCode != null;
export interface AnswerDraft { code: string; answer: string }
export const draftKey = (owner: string, session: string, turn: number) =>
  `interview-answer:${encodeURIComponent(owner)}:${encodeURIComponent(session)}:${turn}`;

export function initialDraft(session: AdaptiveInterviewSession, turn: AdaptiveInterviewTurn): AnswerDraft {
  if (hasAnswer(turn)) return { code: turn.submittedCode ?? '', answer: turn.answer ?? '' };
  if (turn.questionType !== 'CODE_REPAIR') return { code: '', answer: '' };
  const previous = session.turns.filter(item => item.turnIndex < turn.turnIndex
    && item.codeTaskTurnIndex === turn.codeTaskTurnIndex && item.submittedCode != null).slice(-1)[0];
  return { code: previous?.submittedCode ?? turn.codeTask?.initialCode ?? '', answer: '' };
}

export function readDraft(storage: Storage, key: string): AnswerDraft | null {
  const raw = storage.getItem(key);
  if (raw === null) return null;
  const parsed: unknown = JSON.parse(raw);
  if (typeof parsed !== 'object' || parsed === null || !('code' in parsed) || !('answer' in parsed)
    || typeof parsed.code !== 'string' || typeof parsed.answer !== 'string') {
    throw new Error('草稿格式损坏，无法恢复。');
  }
  return { code: parsed.code, answer: parsed.answer };
}

export function useAnswerDraft(options: { owner: string; session: AdaptiveInterviewSession | null }) {
  const { owner, session } = options;
  const turn = session?.turns.find(item => item.turnIndex === session.currentTurn);
  const key = session && turn ? draftKey(owner, session.sessionId, turn.turnIndex) : null;
  const [state, setState] = useState<{ key: string | null; draft: AnswerDraft }>({ key: null, draft: { code: '', answer: '' } });
  const [error, setError] = useState('');
  const accepted = turn ? hasAnswer(turn) : false;
  useEffect(() => {
    if (!session || !turn || !key) return;
    const initial = initialDraft(session, turn);
    setState({ key, draft: initial });
    setError('');
    try {
      if (accepted) window.sessionStorage.removeItem(key);
      else setState({ key, draft: readDraft(window.sessionStorage, key) ?? initial });
    } catch (failure) {
      setError(`草稿恢复失败：${failure instanceof Error ? failure.message : String(failure)}`);
    }
  }, [key, accepted]);
  useAcceptedDraftCleanup({ session, owner, onError: setError });
  const update = (patch: Partial<AnswerDraft>) => {
    const draft = { ...state.draft, ...patch };
    setState({ key, draft });
    if (!key) return;
    try { window.sessionStorage.setItem(key, JSON.stringify(draft)); setError(''); }
    catch (failure) { setError(`草稿保存失败：${failure instanceof Error ? failure.message : String(failure)}。请保留当前页面。`); }
  };
  const clearAccepted = (updated: AdaptiveInterviewSession) => {
    if (!session) return;
    const original = updated.turns.find(item => item.turnIndex === session.currentTurn);
    if (!original || !hasAnswer(original)) return;
    try { window.sessionStorage.removeItem(draftKey(owner, session.sessionId, original.turnIndex)); }
    catch (failure) { setError(`已接受答案，但草稿清理失败：${String(failure)}`); }
  };
  return { draft: state.key === key ? state.draft : { code: '', answer: '' }, update, error, clearAccepted };
}

function useAcceptedDraftCleanup(options: {
  session: AdaptiveInterviewSession | null; owner: string; onError: (error: string) => void;
}) {
  const { session, owner, onError } = options;
  useEffect(() => {
    if (!session) return;
    try {
      session.turns.filter(hasAnswer).forEach(turn =>
        window.sessionStorage.removeItem(draftKey(owner, session.sessionId, turn.turnIndex)));
    } catch (failure) { onError(`已接受答案，但草稿清理失败：${String(failure)}`); }
  }, [session, owner, onError]);
}
