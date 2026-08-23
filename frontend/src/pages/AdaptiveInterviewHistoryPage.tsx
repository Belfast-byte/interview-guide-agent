import { AlertCircle, ArrowRight, Bot, ChevronLeft, ChevronRight, Loader2 } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { adaptiveInterviewApi } from '../api/adaptiveInterview';
import { getErrorMessage } from '../api/request';
import { ROUTES } from '../constants/routes';
import type {
  AdaptiveInterviewHistoryPage,
  AdaptiveInterviewSummary,
  AdaptiveSessionStatus,
} from '../types/adaptiveInterview';
import { formatDateTime } from '../utils/date';

const STATUS_LABELS: Record<AdaptiveSessionStatus, string> = {
  CREATED: '正在准备',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  FAILED: '创建失败',
};

export default function AdaptiveInterviewHistoryPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<AdaptiveInterviewHistoryPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setResult(await adaptiveInterviewApi.history(page));
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="mx-auto max-w-6xl pb-12">
      <header className="mb-6">
        <div className="mb-3 flex items-center gap-2 text-primary-600 dark:text-primary-300"><Bot className="h-5 w-5" /><span className="text-xs font-bold uppercase tracking-wider">Adaptive History</span></div>
        <h1 className="text-3xl font-bold text-slate-950 dark:text-white">自适应面试历史</h1>
        <p className="mt-2 text-sm text-slate-500">继续进行中的会话，或回看已完成面试的问答与评估报告。</p>
      </header>

      {error && <ErrorState message={error} retry={() => void load()} />}
      {loading ? (
        <div className="flex min-h-64 items-center justify-center"><Loader2 className="h-8 w-8 animate-spin text-primary-500" /></div>
      ) : !error && result?.content.length === 0 ? (
        <EmptyState />
      ) : !error && result ? (
        <>
          <div className="space-y-4">
            {result.content.map(interview => <HistoryCard key={interview.sessionId} interview={interview} />)}
          </div>
          <Pagination result={result} onPage={setPage} />
        </>
      ) : null}
    </div>
  );
}

function HistoryCard({ interview }: { interview: AdaptiveInterviewSummary }) {
  const action = interview.status === 'COMPLETED' ? '查看问答与报告' : '进入会话';
  return (
    <Link to={ROUTES.adaptiveInterviewSession(interview.sessionId)} className="group block rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:border-primary-300 hover:shadow-lg dark:border-slate-700 dark:bg-slate-900 dark:hover:border-primary-700">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-3">
            <Status status={interview.status} />
            <span className="text-xs text-slate-400">{formatDateTime(interview.createdAt)}</span>
            <span className="text-xs font-medium text-slate-500">第 {interview.currentTurn} / {interview.maxTurns} 轮</span>
          </div>
          <p className="mt-3 line-clamp-2 text-sm leading-6 text-slate-700 dark:text-slate-200">{interview.jdSummary}</p>
        </div>
        <span className="inline-flex flex-none items-center gap-2 text-sm font-semibold text-primary-600 dark:text-primary-300">{action}<ArrowRight className="h-4 w-4 transition group-hover:translate-x-1" /></span>
      </div>
    </Link>
  );
}

function Status({ status }: { status: AdaptiveSessionStatus }) {
  const tone = status === 'COMPLETED' ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300' : status === 'FAILED' ? 'bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300' : 'bg-primary-50 text-primary-700 dark:bg-primary-950 dark:text-primary-300';
  return <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${tone}`}>{STATUS_LABELS[status]}</span>;
}

function Pagination(props: { result: AdaptiveInterviewHistoryPage; onPage: (page: number) => void }) {
  const result = props.result;
  return (
    <div className="mt-6 flex items-center justify-between rounded-xl border border-slate-200 bg-white px-4 py-3 dark:border-slate-700 dark:bg-slate-900">
      <p className="text-xs text-slate-500">共 {result.totalElements} 场 · 第 {result.page + 1} / {Math.max(result.totalPages, 1)} 页</p>
      <div className="flex gap-2">
        <button type="button" onClick={() => props.onPage(result.page - 1)} disabled={result.page === 0} className="btn-secondary rounded-lg p-2 disabled:opacity-40" aria-label="上一页"><ChevronLeft className="h-4 w-4" /></button>
        <button type="button" onClick={() => props.onPage(result.page + 1)} disabled={result.page + 1 >= result.totalPages} className="btn-secondary rounded-lg p-2 disabled:opacity-40" aria-label="下一页"><ChevronRight className="h-4 w-4" /></button>
      </div>
    </div>
  );
}

function ErrorState({ message, retry }: { message: string; retry: () => void }) {
  return <div role="alert" className="rounded-2xl border border-red-200 bg-red-50 p-5 text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300"><div className="flex items-center gap-2 font-semibold"><AlertCircle className="h-4 w-4" />历史记录加载失败</div><p className="mt-2 text-sm">{message}</p><button type="button" onClick={retry} className="mt-3 text-sm font-semibold underline">重新加载</button></div>;
}

function EmptyState() {
  return <div className="rounded-2xl border border-dashed border-slate-300 bg-white/70 p-12 text-center dark:border-slate-700 dark:bg-slate-900/70"><Bot className="mx-auto h-10 w-10 text-slate-300" /><h2 className="mt-4 font-bold text-slate-800 dark:text-white">还没有自适应面试</h2><Link to={ROUTES.adaptiveInterview} className="btn-primary mt-5 inline-flex rounded-xl px-4 py-2.5">开始第一场面试</Link></div>;
}
