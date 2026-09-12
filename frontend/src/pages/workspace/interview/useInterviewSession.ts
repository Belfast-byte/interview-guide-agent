import { useCallback, useEffect, useState } from 'react';
import { adaptiveInterviewApi, type SubmitAnswerStreamCallbacks } from '../../../api/adaptiveInterview';
import { getErrorMessage } from '../../../api/request';
import type { AdaptiveInterviewSession, SubmitAdaptiveAnswerRequest } from '../../../types/adaptiveInterview';
import { extractPartialContent } from '../../adaptiveInterviewStream';

const POLL_INTERVAL_MS = 2_000;
export function useSessionSnapshot(sessionId: string | undefined) {
  const [session, setSession] = useState<AdaptiveInterviewSession | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [recoveryPending, setRecoveryPending] = useState(false);
  const load = useCallback(async () => {
    if (!sessionId) return;
    setLoading(true); setError('');
    try { setSession(await adaptiveInterviewApi.get(sessionId)); setRecoveryPending(false); }
    catch (failure) { setError(getErrorMessage(failure)); }
    finally { setLoading(false); }
  }, [sessionId]);
  useEffect(() => { setSession(null); void load(); }, [load]);
  return { session, setSession, loading, error, setError, recoveryPending, setRecoveryPending, load };
}

type Snapshot = ReturnType<typeof useSessionSnapshot>;
export function useSessionPolling(snapshot: Snapshot, working: boolean) {
  const { session, setSession, setError, setRecoveryPending } = snapshot;
  const current = session?.turns.find(turn => turn.turnIndex === session.currentTurn);
  useEffect(() => {
    if (!session || working) return;
    if (session.status !== 'CREATED' && !(session.status === 'IN_PROGRESS' && current?.answerStatus === 'PROCESSING')) return;
    let cancelled = false;
    let timer: number;
    const poll = async () => {
      try {
        const updated = await adaptiveInterviewApi.get(session.sessionId);
        if (cancelled) return;
        setSession(updated); setRecoveryPending(false); setError('');
      } catch (failure) {
        if (cancelled) return;
        setError(getErrorMessage(failure));
        timer = window.setTimeout(() => void poll(), POLL_INTERVAL_MS);
      }
    };
    timer = window.setTimeout(() => void poll(), POLL_INTERVAL_MS);
    return () => { cancelled = true; window.clearTimeout(timer); };
  }, [session, current?.answerStatus, working, setSession, setError, setRecoveryPending]);
}

async function consumeAnswer(options: {
  session: AdaptiveInterviewSession; payload: SubmitAdaptiveAnswerRequest | null;
  callbacks: SubmitAnswerStreamCallbacks;
}) {
  if (options.payload === null) {
    await adaptiveInterviewApi.retryAnswerStream(options.session.sessionId, options.session.currentTurn, options.callbacks);
    return;
  }
  await adaptiveInterviewApi.submitAnswerStream(options.session.sessionId, options.payload, options.callbacks);
}

export function useAnswerSubmission(snapshot: Snapshot, accepted: (session: AdaptiveInterviewSession) => void) {
  const [working, setWorking] = useState(false);
  const [stage, setStage] = useState<'assessing' | 'generating' | null>(null);
  const [streamingQuestion, setStreamingQuestion] = useState('');
  const submit = async (payload: SubmitAdaptiveAnswerRequest | null) => {
    const session = snapshot.session;
    if (!session || working || snapshot.recoveryPending) return;
    setWorking(true); snapshot.setError(''); setStage('assessing'); setStreamingQuestion('');
    let streamFailure: Error | null = null;
    let raw = '';
    try {
      await consumeAnswer({ session, payload, callbacks: {
        onStage: setStage,
        onDelta: delta => { raw += delta; setStreamingQuestion(extractPartialContent(raw)); },
        onDone: updated => { accepted(updated); snapshot.setSession(updated); },
        onError: failure => { streamFailure = failure; },
      } });
      if (streamFailure) throw streamFailure;
    } catch (failure) {
      snapshot.setError(getErrorMessage(failure));
      try {
        const updated = await adaptiveInterviewApi.get(session.sessionId);
        accepted(updated); snapshot.setSession(updated); snapshot.setRecoveryPending(false);
      } catch {
        snapshot.setRecoveryPending(true);
        snapshot.setError(`${getErrorMessage(failure)}；暂时无法读取处理状态，请刷新确认。`);
      }
    } finally { setWorking(false); setStage(null); setStreamingQuestion(''); }
  };
  useSessionPolling(snapshot, working);
  return { working, stage, streamingQuestion, submit };
}
