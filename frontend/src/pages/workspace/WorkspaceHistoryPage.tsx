import { AlertCircle, ArrowRight, ArrowUpRight } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { adaptiveInterviewApi } from '../../api/adaptiveInterview';
import { getErrorMessage } from '../../api/request';
import { ROUTES } from '../../constants/routes';
import type {
  AdaptiveInterviewHistoryPage,
  AdaptiveInterviewSummary,
  AdaptiveSessionStatus,
} from '../../types/adaptiveInterview';
import { formatDateTime } from '../../utils/date';

const STATUS_LABELS: Record<AdaptiveSessionStatus, string> = {
  CREATED: '正在准备',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  FAILED: '创建失败',
};

/** 状态标签配色：进行中=朱砂，完成=档案绿，失败=灰 */
const STATUS_STYLES: Record<AdaptiveSessionStatus, { background: string; color: string }> = {
  CREATED: {
    background: 'color-mix(in srgb, var(--cinnabar) 10%, transparent)',
    color: 'var(--cinnabar)',
  },
  IN_PROGRESS: {
    background: 'color-mix(in srgb, var(--cinnabar) 10%, transparent)',
    color: 'var(--cinnabar)',
  },
  COMPLETED: {
    background: 'color-mix(in srgb, #2F6B4F 12%, transparent)',
    color: '#2F6B4F',
  },
  FAILED: {
    background: 'color-mix(in srgb, var(--wk-muted) 12%, transparent)',
    color: 'var(--wk-muted)',
  },
};

export default function WorkspaceHistoryPage() {
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
    <div className="pb-24">
      {/* ===== 页首 ===== */}
      <div className="wk-rise pt-10" style={{ animationDelay: '0.02s' }}>
        <p className="flex items-center gap-3 font-monosc text-[11.5px] tracking-[0.18em] text-cinnabar">
          <span className="h-px w-8 bg-cinnabar" aria-hidden="true" />
          INTERVIEW RECORDS / 面试记录
        </p>
        <h1 className="mt-5 font-serifsc text-[32px] font-black leading-[1.25] tracking-wide text-ink sm:text-[40px]">
          每一场面试，都留有记录。
        </h1>
        <p className="mt-4 max-w-[44em] text-[15px] leading-7 text-wk-muted">
          继续进行中的会话，或回看已完成面试的评估报告。
        </p>
      </div>

      {error && (
        <div className="wk-rise mt-8" style={{ animationDelay: '0.1s' }}>
          <div className="wk-error">
            <AlertCircle className="mt-0.5 h-4 w-4 flex-none" />
            <span>面试记录加载失败：{error}</span>
            <button
              type="button"
              onClick={() => void load()}
              className="ml-auto flex-none font-semibold underline"
            >
              重新加载
            </button>
          </div>
        </div>
      )}

      {loading ? (
        <p className="mt-16 font-monosc text-xs tracking-[0.15em] text-wk-muted">载入中…</p>
      ) : !error && result?.content.length === 0 ? (
        <EmptyState />
      ) : !error && result ? (
        <>
          {/* ===== 记录列表：发丝线行 ===== */}
          <div className="mt-10 border-t border-ink">
            {result.content.map((interview, index) => (
              <HistoryRow key={interview.sessionId} interview={interview} index={index} />
            ))}
          </div>
          <Pagination result={result} onPage={setPage} />
        </>
      ) : null}
    </div>
  );
}

function HistoryRow({ interview, index }: { interview: AdaptiveInterviewSummary; index: number }) {
  const completed = interview.status === 'COMPLETED';
  // 已完成 → 报告页；其余 → 会话页
  const to = completed
    ? ROUTES.workspaceReport(interview.sessionId)
    : ROUTES.workspaceSession(interview.sessionId);
  const action = completed ? '查看报告' : '进入会话';

  return (
    <Link
      to={to}
      className="wk-rise group flex items-baseline gap-4 border-b border-dashed border-line py-5 transition-colors duration-150 hover:bg-raised sm:gap-6"
      style={{ animationDelay: `${0.08 + index * 0.04}s` }}
    >
      {/* 左：mono 日期与编号 */}
      <div className="w-[92px] flex-none sm:w-[128px]">
        <p className="font-monosc text-[11.5px] tracking-wider text-ink-soft">
          {formatDateTime(interview.createdAt)}
        </p>
        <p className="mt-1 font-monosc text-[10.5px] tracking-wider text-wk-muted">
          第 {interview.currentTurn} / {interview.maxTurns} 轮
        </p>
      </div>

      {/* 中：JD 摘要 */}
      <p className="min-w-0 flex-1 line-clamp-2 text-[14.5px] leading-6 text-ink">
        {interview.jdSummary}
      </p>

      {/* 右：状态标签 + 跳转箭头 */}
      <span className="flex flex-none items-center gap-3">
        <span className="wk-tag" style={STATUS_STYLES[interview.status]}>
          {STATUS_LABELS[interview.status]}
        </span>
        <span className="hidden items-center gap-1 text-xs text-wk-muted transition-colors duration-150 group-hover:text-cinnabar sm:inline-flex">
          {action}
          <ArrowUpRight className="h-3.5 w-3.5" />
        </span>
      </span>
    </Link>
  );
}

function Pagination(props: { result: AdaptiveInterviewHistoryPage; onPage: (page: number) => void }) {
  const result = props.result;
  return (
    <div className="mt-8 flex items-center justify-between">
      <p className="font-monosc text-[11px] tracking-wider text-wk-muted">
        共 {result.totalElements} 场 · 第 {result.page + 1} / {Math.max(result.totalPages, 1)} 页
      </p>
      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => props.onPage(result.page - 1)}
          disabled={result.page === 0}
          className="wk-btn-ghost"
        >
          <ArrowRight className="h-3 w-3 rotate-180" />
          上一页
        </button>
        <button
          type="button"
          onClick={() => props.onPage(result.page + 1)}
          disabled={result.page + 1 >= result.totalPages}
          className="wk-btn-ghost"
        >
          下一页
          <ArrowRight className="h-3 w-3" />
        </button>
      </div>
    </div>
  );
}

function EmptyState() {
  return (
    <div className="wk-rise mt-16 max-w-[36em]" style={{ animationDelay: '0.1s' }}>
      <p className="text-[15px] leading-7 text-wk-muted">
        还没有面试记录。准备好 JD 和简历，先走完一场，这里才会长出第一条记录。
      </p>
      <Link to={ROUTES.workspace} className="wk-btn-ghost mt-5">
        去安排一场面试
        <ArrowRight className="h-3 w-3" />
      </Link>
    </div>
  );
}
