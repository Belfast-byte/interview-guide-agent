import { Check } from 'lucide-react';
import { Link } from 'react-router-dom';
import { ROUTES } from '../../../constants/routes';
import type { AdaptiveInterviewDimension, AdaptiveInterviewSession } from '../../../types/adaptiveInterview';

export function DimensionStrip({ dimensions }: { dimensions: AdaptiveInterviewDimension[] }) {
  return <ol className="wk-rise mt-8 grid border-y border-line md:grid-flow-col md:auto-cols-fr">
    {dimensions.map(dimension => <li key={dimension.order}
      className="flex min-w-0 items-center gap-3 border-b border-line px-4 py-3.5 last:border-b-0 md:border-b-0 md:border-r md:last:border-r-0">
      <span className={`flex h-6 w-6 flex-none items-center justify-center rounded-full font-monosc text-[11px] ${dimension.status === 'ACTIVE' ? 'border border-cinnabar text-cinnabar' : 'border border-line text-wk-muted'}`}>
        {dimension.status === 'COMPLETED' ? <Check className="h-3 w-3" /> : dimension.order + 1}</span>
      <div className="min-w-0"><p className="truncate text-[13px] font-semibold">{dimension.dimension}</p>
        <p className="truncate font-monosc text-[10.5px] text-wk-muted">{dimension.completedTurns}/{dimension.allocatedTurns} · {dimension.focus}</p></div>
    </li>)}
  </ol>;
}

export function SessionDetails({ session }: { session: AdaptiveInterviewSession }) {
  const current = session.dimensions.find(item => item.status === 'ACTIVE');
  return <aside className="space-y-6 border-t border-line pt-5">
    {session.status === 'COMPLETED' ? <div className="wk-docket"><p className="wk-label">本场面试已完成</p>
      <Link to={ROUTES.workspaceReport(session.sessionId)} className="wk-cta mt-4">查看评估报告</Link></div>
      : current && <div className="wk-docket"><p className="wk-label">当前考察重点</p>
        <p className="mt-3 font-serifsc text-xl font-black">{current.dimension}</p>
        <p className="mt-2 text-sm text-wk-muted">{current.focus}</p></div>}
    <p className="text-xs text-wk-muted">{session.mode === 'EVALUATION' ? '评估模式' : '练习模式'} · 第 {session.currentTurn} / {session.maxTurns} 轮</p>
  </aside>;
}
