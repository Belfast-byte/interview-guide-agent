import { useEffect, useState } from 'react';
import { adaptiveInterviewApi } from '../../../api/adaptiveInterview';
import { getErrorMessage } from '../../../api/request';
import type { AdaptiveInterviewTurn } from '../../../types/adaptiveInterview';
import CodeRepairHistory from '../../../components/codeRepair/CodeRepairHistory';

export default function ReportCodeReviews({ sessionId }: { sessionId: string }) {
  const [turns, setTurns] = useState<AdaptiveInterviewTurn[]>([]);
  const [error, setError] = useState('');
  useEffect(() => {
    let cancelled = false;
    adaptiveInterviewApi.get(sessionId).then(session => {
      if (!cancelled) setTurns(session.turns.filter(turn => turn.questionType === 'CODE_REPAIR' && turn.submittedCode != null));
    }).catch(failure => { if (!cancelled) setError(`代码审阅读取失败：${getErrorMessage(failure)}`); });
    return () => { cancelled = true; };
  }, [sessionId]);
  if (error) return <p role="alert" className="wk-error mt-6">{error}</p>;
  if (!turns.length) return null;
  return <section className="mt-10 border-t border-ink pt-6"><h2 className="font-serifsc text-xl font-bold">逐轮代码审阅</h2>
    {turns.map(turn => <details key={turn.turnIndex} className="mt-4 border-b border-line pb-4">
      <summary className="cursor-pointer text-sm leading-7">第 {turn.turnIndex} 轮 · {turn.question}</summary>
      {turn.answer && <p className="mt-4 whitespace-pre-wrap text-sm">修改说明：{turn.answer}</p>}
      <CodeRepairHistory turn={turn} /></details>)}
  </section>;
}
