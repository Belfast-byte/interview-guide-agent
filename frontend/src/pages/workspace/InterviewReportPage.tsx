import ReportRecommendations from './interview/ReportRecommendations';
import ReportCodeReviews from './interview/ReportCodeReviews';
import ReportEvidence from './interview/ReportEvidence';
import { useCallback, useEffect, useState } from 'react';
import { AlertCircle, ArrowLeft, Loader2, RefreshCw } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { adaptiveInterviewApi } from '../../api/adaptiveInterview';
import { getErrorMessage } from '../../api/request';
import { ROUTES } from '../../constants/routes';
import type {
  AdaptiveAssessmentReport,
  AdaptiveDepthLevel,
} from '../../types/adaptiveInterview';

const DEPTH_LABELS: Record<AdaptiveDepthLevel, string> = {
  L0: '尚无证据',
  L1: '概念识别',
  L2: '实际应用',
  L3: '权衡分析',
  L4: '迁移洞察',
};

const DEPTH_ORDER: AdaptiveDepthLevel[] = ['L0', 'L1', 'L2', 'L3', 'L4'];

export default function InterviewReportPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const [report, setReport] = useState<AdaptiveAssessmentReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadReport = useCallback(async (id: string) => {
    setLoading(true);
    setError('');
    try {
      setReport(await adaptiveInterviewApi.getReport(id));
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (sessionId) void loadReport(sessionId);
  }, [loadReport, sessionId]);

  if (!report) return <ReportUnavailable loading={loading} error={error} sessionId={sessionId} loadReport={loadReport} />;

  return (
    <div className="pb-24">
      {/* ===== 页首：评估档案 ===== */}
      <ReportHeader report={report} />

      <div className="mt-10 grid items-start gap-10 lg:grid-cols-[7fr_5fr]">
        {/* ===== 左：维度结论 ===== */}
        <ReportDimensions report={report} />

        {/* ===== 右：薄弱点 + 练习建议 ===== */}
        <ReportRecommendations report={report} />
      </div>
      <ReportCodeReviews sessionId={report.sessionId} />
    </div>
  );
}

/* ===== 深度刻度尺：L0–L4 五档 ===== */
function DepthRuler({ level }: { level: AdaptiveDepthLevel }) {
  const activeIndex = DEPTH_ORDER.indexOf(level);
  return (
    <div>
      <div className="flex items-center gap-1.5" role="img" aria-label={`深度等级 ${level} ${DEPTH_LABELS[level]}`}>
        {DEPTH_ORDER.map((tick, index) => (
          <span
            key={tick}
            className="h-[10px] w-8 rounded-[1px]"
            style={{
              background: index <= activeIndex && level !== 'L0'
                ? 'var(--cinnabar)'
                : 'var(--line)',
            }}
          />
        ))}
        <span className="ml-2 font-monosc text-xs font-medium text-cinnabar">{level}</span>
      </div>
      <p className="mt-1.5 font-monosc text-[11px] tracking-wider text-wk-muted">{DEPTH_LABELS[level]}</p>
    </div>
  );
}

function ReportHeader({ report }: { report: AdaptiveAssessmentReport }) {
  return (
      <header className="wk-rise pt-10">
        <Link
          to={ROUTES.workspaceHistory}
          className="mb-5 inline-flex items-center gap-2 text-[13px] text-wk-muted transition-colors hover:text-ink"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          面试记录
        </Link>
        <div className="flex items-start justify-between gap-6">
          <div>
            <p className="flex items-center gap-3 font-monosc text-[11px] tracking-[0.16em] text-cinnabar">
              <span className="h-px w-8 bg-cinnabar" aria-hidden="true" />
              ASSESSMENT REPORT / 评估报告
            </p>
            <h1 className="mt-3 font-serifsc text-[30px] font-black tracking-wide text-ink sm:text-[38px]">
              评估报告
            </h1>
            <p className="mt-2 max-w-[40em] text-sm leading-6 text-wk-muted">
              已评估维度展示实际表现与可用证据；未考察维度不作评级。
            </p>
            <p className="mt-3 font-monosc text-[11px] tracking-wider text-wk-muted">
              SESSION {report.sessionId}
            </p>
          </div>
          <div className="wk-seal flex-none" data-ready="true">
            已生成
          </div>
        </div>
      </header>
  );
}

function ReportDimensions({ report }: { report: AdaptiveAssessmentReport }) {
  return (
        <div>
          <p className="wk-label mb-2">Dimension conclusions · {report.dimensions.length} 个维度</p>
          {report.dimensions.map((dimension, index) => (
            <DimensionConclusion key={dimension.order} dimension={dimension} index={index} />
          ))}
        </div>
  );
}

function DimensionConclusion({ dimension, index }: { dimension: AdaptiveAssessmentReport["dimensions"][number]; index: number }) {
  return (
            <section
              key={dimension.order}
              className="wk-rise border-t border-ink py-7 first-of-type:border-ink [&+section]:border-line"
              style={{ animationDelay: `${0.12 + index * 0.05}s` }}
            >
              <div className="flex items-baseline gap-3.5">
                <span className="font-monosc text-xs tracking-wider text-cinnabar">
                  {String(index + 1).padStart(2, '0')}
                </span>
                <h2 className="font-serifsc text-lg font-bold text-ink">{dimension.dimension}</h2>
                <span className="ml-auto font-monosc text-[11px] text-wk-muted">
                  {dimension.confidence === null ? '未考察' : `置信度 ${Math.round(dimension.confidence * 100)}%`}
                </span>
              </div>
              <p className="mt-1.5 pl-8 text-[13px] text-wk-muted">{dimension.focus}</p>

              <div className="mt-4 pl-8">
                {dimension.depthLevel === null ? <p className="text-sm text-wk-muted">未考察，不作评级</p> : <DepthRuler level={dimension.depthLevel} />}
              </div>

              <p className="mt-4 pl-8 text-sm leading-7 text-ink-soft">{dimension.rationale}</p>

              {dimension.evidences.length > 0 && (
                <div className="mt-4 space-y-2.5 pl-8">
                  {dimension.evidences.map((evidence, evidenceIndex) => (
                    <ReportEvidence key={`${evidence.turnIndex}-${evidenceIndex}`} evidence={evidence} />
                  ))}
                </div>
              )}
            </section>
  );
}

function ReportUnavailable({ loading, error, sessionId, loadReport }: {
  loading: boolean; error: string; sessionId?: string; loadReport: (id: string) => Promise<void>;
}) {
  if (loading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <p className="flex items-center gap-3 font-monosc text-xs tracking-wider text-wk-muted">
          <Loader2 className="h-4 w-4 animate-spin text-cinnabar" />
          正在组装可追溯报告…
        </p>
      </div>
    );
  }

  {
    return (
      <div className="pt-16">
        <div className="wk-error max-w-xl">
          <AlertCircle className="mt-0.5 h-4 w-4 flex-none" />
          <span>{error || '报告不存在。'}</span>
        </div>
        <div className="mt-4 flex gap-3">
          {sessionId && (
            <button type="button" onClick={() => void loadReport(sessionId)} className="wk-btn-ghost">
              <RefreshCw className="h-3.5 w-3.5" />
              重新加载
            </button>
          )}
          <Link to={ROUTES.workspaceHistory} className="wk-btn-ghost">
            <ArrowLeft className="h-3.5 w-3.5" />
            返回面试记录
          </Link>
        </div>
      </div>
    );
  }

}
