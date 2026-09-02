import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  Check,
  Clock3,
  Code2,
  Loader2,
  Play,
  RefreshCw,
  Send,
} from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { adaptiveInterviewApi } from '../../api/adaptiveInterview';
import { getErrorMessage } from '../../api/request';
import { ROUTES } from '../../constants/routes';
import { extractPartialContent } from '../adaptiveInterviewStream';
import type {
  AdaptiveInterviewDimension,
  AdaptiveInterviewSession,
  PublicAlgorithmProblem,
  SandboxExecution,
  SandboxLanguage,
  SandboxRunMode,
} from '../../types/adaptiveInterview';

export default function InterviewSessionPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const [session, setSession] = useState<AdaptiveInterviewSession | null>(null);
  const [answer, setAnswer] = useState('');
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState('');
  const [problemId, setProblemId] = useState('');
  const [workloadType, setWorkloadType] = useState<'ALGORITHM' | 'PATCH'>('ALGORITHM');
  const [problem, setProblem] = useState<PublicAlgorithmProblem | null>(null);
  const [problemLoading, setProblemLoading] = useState(false);
  const [language, setLanguage] = useState<SandboxLanguage>('JAVA');
  const [runMode, setRunMode] = useState<SandboxRunMode>('SAMPLE');
  const [source, setSource] = useState('');
  const [submission, setSubmission] = useState<SandboxExecution | null>(null);
  const [judging, setJudging] = useState(false);
  const [judgeError, setJudgeError] = useState('');
  const [answerStage, setAnswerStage] = useState<'assessing' | 'generating' | null>(null);
  const [streamingQuestion, setStreamingQuestion] = useState('');
  // 当前查看的题号；null 表示跟随最新进度（待回答题 / 出题中）
  const [viewIndex, setViewIndex] = useState<number | null>(null);
  const creationStreamActive = useRef(false);

  const loadSession = useCallback(async (id: string) => {
    setLoading(true);
    setError('');
    try {
      setSession(await adaptiveInterviewApi.get(id));
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (sessionId && !creationStreamActive.current) {
      void loadSession(sessionId);
    }
  }, [loadSession, sessionId]);

  // 切换会话时回到最新题页
  useEffect(() => {
    setViewIndex(null);
  }, [sessionId]);

  // 页面刷新后无法重连原 POST 流，仅对这种恢复场景轮询 CREATED 会话。
  useEffect(() => {
    if (!sessionId || session?.status !== 'CREATED' || creationStreamActive.current) return;
    const timer = window.setTimeout(() => void loadSession(sessionId), 2_000);
    return () => window.clearTimeout(timer);
  }, [loadSession, session?.status, sessionId]);

  const completedTurns = useMemo(
    () => session?.turns.filter(turn => turn.answer !== null).length ?? 0,
    [session?.turns],
  );
  const currentDimension = useMemo(
    () => session?.dimensions.find(dimension => dimension.status === 'ACTIVE'),
    [session?.dimensions],
  );
  const codeWorkbenchActive = Boolean(
    currentDimension && /算法|数据结构|algorithm|项目|代码|优化/i.test(
      `${currentDimension.dimension} ${currentDimension.focus}`,
    ),
  );

  // 判题结果轮询
  useEffect(() => {
    if (!session || !submission || !['PENDING', 'RUNNING'].includes(submission.status)) return;
    const timer = window.setTimeout(async () => {
      try {
        setSubmission(await adaptiveInterviewApi.getCodeSubmission(session.sessionId, submission.submissionId));
      } catch (requestError) {
        setJudgeError(getErrorMessage(requestError));
      }
    }, 2_000);
    return () => window.clearTimeout(timer);
  }, [session, submission]);

  const submitAnswer = async (activeSession: AdaptiveInterviewSession) => {
    const content = answer.trim();
    if (!content) {
      setError('回答不能为空。请说明你的判断、做法和取舍。');
      return;
    }

    setWorking(true);
    setError('');
    setAnswerStage('assessing');
    setStreamingQuestion('');
    // 乐观更新：回答立即写入当前轮，清空输入框；失败时回滚
    setSession(prev => prev === null ? prev : {
      ...prev,
      turns: prev.turns.map(turn =>
        turn.turnIndex === activeSession.currentTurn ? { ...turn, answer: content } : turn),
    });
    setAnswer('');

    let rawDecision = '';
    await adaptiveInterviewApi.submitAnswerStream(activeSession.sessionId, {
      turnIndex: activeSession.currentTurn,
      answer: content,
    }, {
      onStage: setAnswerStage,
      onDelta: delta => {
        rawDecision += delta;
        setStreamingQuestion(extractPartialContent(rawDecision));
      },
      onDone: updated => {
        setSession(updated);
        setStreamingQuestion('');
        setAnswerStage(null);
        setViewIndex(null);
      },
      onError: streamError => {
        setSession(prev => prev === null ? prev : {
          ...prev,
          turns: prev.turns.map(turn =>
            turn.turnIndex === activeSession.currentTurn && turn.answer === content
              ? { ...turn, answer: null }
              : turn),
        });
        setAnswer(content);
        setStreamingQuestion('');
        setAnswerStage(null);
        setViewIndex(null);
        setError(getErrorMessage(streamError));
      },
    });
    setWorking(false);
  };

  const submitCode = async (activeSession: AdaptiveInterviewSession) => {
    if (!problemId.trim() || !source.trim()) {
      setJudgeError(workloadType === 'PATCH' ? '请填写场景标识和补丁后再提交。' : '请填写题目标识和代码后再运行。');
      return;
    }
    setJudging(true);
    setJudgeError('');
    try {
      const turnIndex = activeSession.currentTurn;
      if (workloadType === 'ALGORITHM' && runMode === 'SAMPLE') {
        setSubmission(await adaptiveInterviewApi.submitCode(activeSession.sessionId, {
          turnIndex,
          problemId: problemId.trim(),
          language,
          source,
          runMode,
        }));
      } else {
        const updated = await adaptiveInterviewApi.submitAnswer(activeSession.sessionId, {
          turnIndex,
          answer: source,
          codeSubmission: {
            ...(workloadType === 'PATCH'
              ? { scenarioId: problemId.trim() }
              : { problemId: problemId.trim() }),
            language,
            runMode: workloadType === 'PATCH' ? 'FULL' : runMode,
          },
        });
        setSession(updated);
        setSubmission(await adaptiveInterviewApi.getLatestCodeSubmission(activeSession.sessionId, turnIndex));
      }
    } catch (requestError) {
      setJudgeError(getErrorMessage(requestError));
    } finally {
      setJudging(false);
    }
  };

  const loadProblemVariant = async (activeSession: AdaptiveInterviewSession) => {
    if (!problemId.trim()) {
      setJudgeError('请先填写一个题目标识作为变体组入口。');
      return;
    }
    setProblemLoading(true);
    setJudgeError('');
    try {
      const selected = await adaptiveInterviewApi.selectProblemVariant(activeSession.sessionId, problemId.trim());
      setProblem(selected);
      setProblemId(selected.id);
    } catch (requestError) {
      setJudgeError(getErrorMessage(requestError));
    } finally {
      setProblemLoading(false);
    }
  };

  if (loading && !session) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <p className="flex items-center gap-3 font-monosc text-xs tracking-wider text-wk-muted">
          <Loader2 className="h-4 w-4 animate-spin text-cinnabar" />
          正在读取面试快照…
        </p>
      </div>
    );
  }

  if (!session) {
    return (
      <div className="pt-16">
        <div className="wk-error max-w-xl">
          <AlertCircle className="mt-0.5 h-4 w-4 flex-none" />
          <span>{error || '会话不存在或已被删除。'}</span>
        </div>
        <Link to={ROUTES.workspaceHistory} className="wk-btn-ghost mt-4">
          <ArrowLeft className="h-3.5 w-3.5" />
          返回面试记录
        </Link>
      </div>
    );
  }

  // ===== 翻页派生状态 =====
  const turns = session.turns;
  const lastTurnIndex = turns.length > 0 ? turns[turns.length - 1].turnIndex : null;
  // 待回答题：进行中且当前轮还没有答案
  const answerableIndex = session.status === 'IN_PROGRESS'
    && turns.some(turn => turn.turnIndex === session.currentTurn && turn.answer === null)
    ? session.currentTurn
    : null;
  // “最新一页”的题号：提交后（working）是正在生成的新题，否则是待回答题或最后一题
  const liveIndex = working
    ? (lastTurnIndex ?? 0) + 1
    : (answerableIndex ?? lastTurnIndex ?? 1);
  const viewingLive = viewIndex === null;
  const viewedIndex = viewingLive ? liveIndex : viewIndex;
  const viewedTurn = turns.find(turn => turn.turnIndex === viewedIndex) ?? null;
  const isAnswerPage = viewingLive && !working && answerableIndex !== null;
  const canPrev = turns.length > 0 && viewedIndex > turns[0].turnIndex;
  const canNext = !viewingLive;
  const goPrev = () => {
    if (canPrev) setViewIndex(viewedIndex - 1);
  };
  const goNext = () => {
    if (viewIndex === null) return;
    const liveTarget = working ? (lastTurnIndex ?? 0) + 1 : (answerableIndex ?? (lastTurnIndex ?? 0) + 1);
    setViewIndex(viewIndex + 1 >= liveTarget ? null : viewIndex + 1);
  };

  const statusLabel = session.status === 'CREATED'
    ? '正在准备'
    : session.status === 'FAILED'
      ? '创建失败'
      : session.status === 'COMPLETED'
        ? '已完成'
        : `第 ${session.currentTurn} / ${session.maxTurns} 轮`;

  return (
    <div className="pb-24">
      {/* ===== 页首 ===== */}
      <header className="wk-rise flex flex-col gap-5 pt-10 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <Link
            to={ROUTES.workspaceHistory}
            className="mb-5 inline-flex items-center gap-2 text-[13px] text-wk-muted transition-colors hover:text-ink"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            面试记录
          </Link>
          <p className="flex items-center gap-3 font-monosc text-[11px] tracking-[0.16em] text-cinnabar">
            <span className="h-px w-8 bg-cinnabar" aria-hidden="true" />
            {session.runtimeVersion}
            {session.llmProviderName && (
              <span className="text-wk-muted">{session.llmProviderName} · {session.llmModel}</span>
            )}
          </p>
          <h1 className="mt-3 font-serifsc text-[28px] font-black tracking-wide text-ink sm:text-[34px]">
            自适应技术面试
          </h1>
        </div>
        <div className="flex items-center gap-3">
          <span
            className="wk-tag"
            style={{
              background: session.status === 'FAILED'
                ? 'var(--cinnabar-wash)'
                : 'color-mix(in srgb, var(--ink) 7%, transparent)',
              color: session.status === 'FAILED' ? 'var(--cinnabar)' : 'var(--ink-soft)',
            }}
          >
            {statusLabel}
          </span>
          <button
            type="button"
            onClick={() => void loadSession(session.sessionId)}
            disabled={loading || working}
            className="wk-btn-ghost"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            刷新
          </button>
        </div>
      </header>

      {error && (
        <div className="wk-error mt-6">
          <AlertCircle className="mt-0.5 h-4 w-4 flex-none" />
          <span>{error}</span>
        </div>
      )}

      {session.status === 'FAILED' ? (
        <div className="wk-error mt-6">
          <AlertCircle className="mt-0.5 h-4 w-4 flex-none" />
          <span>面试创建失败：{session.failureReason ?? '未知原因'}</span>
          <button
            type="button"
            onClick={() => navigate(ROUTES.workspace)}
            className="ml-auto flex-none font-semibold underline"
          >
            重新开始
          </button>
        </div>
      ) : (
        <DimensionStrip dimensions={session.dimensions} />
      )}

      {/* ===== 主区 ===== */}
      <div className="mt-8 grid items-start gap-10 lg:grid-cols-[7fr_5fr]">
        <section>
          {/* ===== 翻页导航：一题一页，可回看 ===== */}
          {session.status !== 'CREATED' && turns.length > 0 && (
            <div
              className="wk-rise flex items-center justify-between border-y border-line py-2.5"
              style={{ animationDelay: '0.06s' }}
            >
              <button type="button" onClick={goPrev} disabled={!canPrev} className="wk-btn-ghost">
                <ArrowLeft className="h-3.5 w-3.5" />
                上一题
              </button>
              <p className="font-monosc text-[11.5px] tracking-[0.15em] text-wk-muted">
                Q{String(viewedIndex).padStart(2, '0')} / {String(session.maxTurns).padStart(2, '0')}
                {isAnswerPage && <span className="text-cinnabar"> · 待回答</span>}
                {viewingLive && working && <span className="text-cinnabar"> · 出题中</span>}
              </p>
              <button type="button" onClick={goNext} disabled={!canNext} className="wk-btn-ghost">
                下一题
                <ArrowRight className="h-3.5 w-3.5" />
              </button>
            </div>
          )}

          {/* ===== 题页 ===== */}
          <div key={viewedIndex} className="mt-8">
            {viewedTurn === null ? (
              session.status === 'CREATED' && !streamingQuestion ? (
                <div className="flex items-center gap-3 border-l-2 border-cinnabar bg-cinnabar-wash/60 px-4 py-3.5">
                  <Loader2 className="h-4 w-4 flex-none animate-spin text-cinnabar" />
                  <p className="text-sm leading-6 text-ink-soft">
                    正在根据职位描述与简历规划面试维度并生成首题，完成后自动进入面试。
                  </p>
                </div>
              ) : streamingQuestion ? (
                <div className="border-l-2 border-ink pl-5">
                  <p className="mb-1.5 font-monosc text-[10.5px] uppercase tracking-[0.14em] text-cinnabar">
                    Q{String(viewedIndex).padStart(2, '0')}
                  </p>
                  <p className="whitespace-pre-wrap text-[15px] font-medium leading-8 text-ink">
                    {streamingQuestion}
                    <span className="ml-0.5 inline-block h-4 w-[2px] animate-pulse bg-cinnabar align-middle" />
                  </p>
                </div>
              ) : (
                <p className="flex items-center gap-3 font-monosc text-xs tracking-wider text-wk-muted">
                  <Loader2 className="h-3.5 w-3.5 animate-spin text-cinnabar" />
                  {answerStage === 'assessing' ? '正在评估你的回答…' : '正在生成下一题…'}
                </p>
              )
            ) : (
              <article className="wk-rise space-y-5">
                {/* 提问 */}
                <div className="border-l-2 border-ink pl-5">
                  <p className="mb-1.5 font-monosc text-[10.5px] uppercase tracking-[0.14em] text-cinnabar">
                    Q{String(viewedTurn.turnIndex).padStart(2, '0')}
                  </p>
                  <p className="whitespace-pre-wrap text-[15px] font-medium leading-8 text-ink">
                    {viewedTurn.question}
                  </p>
                </div>

                {isAnswerPage ? (
                  /* 待回答页：可编辑输入区 */
                  <div className="border-t border-line pt-6">
                    <label htmlFor="workspace-answer" className="wk-label">你的回答</label>
                    <textarea
                      id="workspace-answer"
                      value={answer}
                      onChange={event => setAnswer(event.target.value)}
                      onKeyDown={event => {
                        if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
                          event.preventDefault();
                          void submitAnswer(session);
                        }
                      }}
                      rows={8}
                      disabled={working}
                      placeholder="说明判断依据、实施方式、边界条件和取舍。"
                      className="wk-input mt-2 resize-y leading-7"
                    />
                    <div className="mt-3 flex items-center justify-between gap-4">
                      <p className="font-monosc text-[11px] text-wk-muted">Ctrl / ⌘ + Enter 提交</p>
                      <button
                        type="button"
                        onClick={() => void submitAnswer(session)}
                        disabled={working || !answer.trim()}
                        className="wk-cta px-5 py-2.5 text-sm"
                      >
                        {working ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
                        {working
                          ? (answerStage === 'generating' ? '正在生成下一题' : '正在评估你的回答')
                          : '提交回答'}
                      </button>
                    </div>
                  </div>
                ) : viewedTurn.answer !== null ? (
                  /* 回看页：答案只读，提交置灰 */
                  <>
                    <div className="ml-6 border-l-2 border-line bg-raised px-5 py-4">
                      <p className="mb-1.5 font-monosc text-[10.5px] uppercase tracking-[0.14em] text-wk-muted">
                        你的回答
                      </p>
                      <p className="whitespace-pre-wrap text-sm leading-7 text-ink-soft">{viewedTurn.answer}</p>
                    </div>
                    <div className="ml-6 flex justify-end">
                      <button type="button" disabled className="wk-cta px-5 py-2.5 text-sm">
                        <Check className="h-4 w-4" />
                        已提交
                      </button>
                    </div>
                  </>
                ) : null}
              </article>
            )}
          </div>

          {/* 代码判题工单 */}
          {session.status === 'IN_PROGRESS' && codeWorkbenchActive && viewingLive && (
            <CodeWorkbench
              workloadType={workloadType}
              problemId={problemId}
              problem={problem}
              problemLoading={problemLoading}
              language={language}
              runMode={runMode}
              source={source}
              submission={submission}
              judging={judging}
              error={judgeError}
              onProblemIdChange={value => {
                setProblemId(value);
                setProblem(null);
              }}
              onWorkloadTypeChange={value => {
                setWorkloadType(value);
                setProblem(null);
                if (value === 'PATCH') setRunMode('FULL');
              }}
              onLoadProblem={() => void loadProblemVariant(session)}
              onLanguageChange={setLanguage}
              onRunModeChange={setRunMode}
              onSourceChange={setSource}
              onRun={() => void submitCode(session)}
            />
          )}
        </section>

        {/* ===== 右栏 ===== */}
        <aside className="space-y-6 lg:sticky lg:top-20">
          {session.status === 'COMPLETED' ? (
            <div className="wk-docket">
              <p className="font-serifsc text-lg font-black tracking-wide text-ink">面试已完成</p>
              <p className="mt-2 text-sm leading-6 text-wk-muted">
                共 {completedTurns} 轮问答。评估报告由已验证的证据确定性生成，每个结论都能回到原始问答。
              </p>
              <Link
                to={ROUTES.workspaceReport(session.sessionId)}
                className="wk-cta mt-5 w-full text-sm"
              >
                查看评估报告
                <ArrowRight className="h-4 w-4" />
              </Link>
            </div>
          ) : (
            <CurrentFocus dimensions={session.dimensions} />
          )}

          <div className="border-t border-line pt-4">
            <p className="wk-label mb-3">会话信息</p>
            <dl className="space-y-2 text-[13px]">
              <div className="flex justify-between gap-4">
                <dt className="text-wk-muted">模式</dt>
                <dd className="font-medium text-ink">{session.mode === 'EVALUATION' ? '评估模式' : '练习模式'}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-wk-muted">阶段</dt>
                <dd className="font-medium text-ink">
                  {session.candidateLevel === 'INTERN' ? '实习' : session.candidateLevel === 'CAMPUS' ? '校招' : '社招'}
                </dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-wk-muted">进度</dt>
                <dd className="font-monosc text-ink">{session.currentTurn} / {session.maxTurns}</dd>
              </div>
            </dl>
          </div>
        </aside>
      </div>
    </div>
  );
}

/* ===== 维度进度条：发丝线步进器 ===== */
function DimensionStrip({ dimensions }: { dimensions: AdaptiveInterviewDimension[] }) {
  return (
    <ol className="wk-rise mt-8 grid border-y border-line md:grid-flow-col md:auto-cols-fr" style={{ animationDelay: '0.1s' }}>
      {dimensions.map(dimension => (
        <li
          key={dimension.order}
          className="flex min-w-0 items-center gap-3 border-b border-line px-4 py-3.5 last:border-b-0 md:border-b-0 md:border-r md:last:border-r-0"
        >
          <span
            className={`flex h-6 w-6 flex-none items-center justify-center rounded-full font-monosc text-[11px] font-medium ${
              dimension.status === 'COMPLETED'
                ? 'bg-[#2F6B4F] text-white'
                : dimension.status === 'ACTIVE'
                  ? 'border-[1.5px] border-cinnabar text-cinnabar'
                  : 'border border-line text-wk-muted'
            }`}
          >
            {dimension.status === 'COMPLETED'
              ? <Check className="h-3 w-3" />
              : dimension.order + 1}
          </span>
          <div className="min-w-0">
            <p className={`truncate text-[13px] font-semibold ${dimension.status === 'ACTIVE' ? 'text-ink' : 'text-ink-soft'}`}>
              {dimension.dimension}
            </p>
            <p className="truncate font-monosc text-[10.5px] text-wk-muted">
              {dimension.completedTurns}/{dimension.allocatedTurns} · {dimension.focus}
            </p>
          </div>
        </li>
      ))}
    </ol>
  );
}

/* ===== 当前考察重点 ===== */
function CurrentFocus({ dimensions }: { dimensions: AdaptiveInterviewDimension[] }) {
  const current = dimensions.find(dimension => dimension.status === 'ACTIVE');
  if (!current) return null;
  const progress = current.allocatedTurns > 0
    ? Math.min(100, (current.completedTurns / current.allocatedTurns) * 100)
    : 0;
  return (
    <div className="wk-docket">
      <p className="wk-label">当前考察重点</p>
      <p className="mt-3 font-serifsc text-xl font-black tracking-wide text-ink">{current.dimension}</p>
      <p className="mt-1.5 text-sm leading-6 text-wk-muted">{current.focus}</p>
      <div className="mt-5 flex items-center gap-3">
        <div className="h-[3px] flex-1 bg-line">
          <div className="h-full bg-cinnabar transition-all duration-500" style={{ width: `${progress}%` }} />
        </div>
        <span className="font-monosc text-[11px] text-wk-muted">
          {current.completedTurns}/{current.allocatedTurns}
        </span>
      </div>
    </div>
  );
}

/* ===== 代码判题工单：终端风墨块 ===== */
interface CodeWorkbenchProps {
  workloadType: 'ALGORITHM' | 'PATCH';
  problemId: string;
  problem: PublicAlgorithmProblem | null;
  problemLoading: boolean;
  language: SandboxLanguage;
  runMode: SandboxRunMode;
  source: string;
  submission: SandboxExecution | null;
  judging: boolean;
  error: string;
  onProblemIdChange: (value: string) => void;
  onWorkloadTypeChange: (value: 'ALGORITHM' | 'PATCH') => void;
  onLoadProblem: () => void;
  onLanguageChange: (value: SandboxLanguage) => void;
  onRunModeChange: (value: SandboxRunMode) => void;
  onSourceChange: (value: string) => void;
  onRun: () => void;
}

function CodeWorkbench(props: CodeWorkbenchProps) {
  const running = props.judging
    || props.submission?.status === 'PENDING'
    || props.submission?.status === 'RUNNING';
  const wbInput = 'w-full rounded-[3px] border border-white/15 bg-white/5 px-3 py-2.5 font-monosc text-xs text-[#EDEDE6] outline-none transition-colors placeholder:text-[#6E7A78] focus:border-cinnabar';
  const wbSeg = (active: boolean) =>
    `rounded-[3px] border px-3 py-2 text-xs font-semibold transition-colors ${
      active ? 'border-cinnabar bg-cinnabar/15 text-[#F2B4A6]' : 'border-white/15 text-[#9A9A90] hover:border-white/30'
    }`;

  return (
    <section className="mt-10 rounded border border-white/10 bg-[#141B1D] text-[#EDEDE6]">
      <div className="flex flex-col gap-4 border-b border-white/10 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <Code2 className="h-4 w-4 text-cinnabar" />
          <div>
            <p className="font-monosc text-[10px] uppercase tracking-[0.18em] text-cinnabar">Judge docket</p>
            <h2 className="mt-0.5 text-sm font-semibold">代码判题工单</h2>
          </div>
        </div>
        <div className="flex items-center gap-2 font-monosc text-[10px] uppercase tracking-wider text-[#9A9A90]">
          {['PENDING', 'RUNNING', 'DONE'].map((status, index) => {
            const active = props.submission && (
              props.submission.status === status
              || (status === 'DONE' && props.submission.status === 'TIMEOUT_QUEUED')
            );
            return (
              <span
                key={status}
                className={`rounded-[3px] border px-2 py-1 ${active ? 'border-cinnabar/60 bg-cinnabar/10 text-[#F2B4A6]' : 'border-white/15'}`}
              >
                {index + 1}. {status}
              </span>
            );
          })}
        </div>
      </div>

      <div className="grid gap-5 px-5 py-5 lg:grid-cols-[minmax(0,1fr)_230px]">
        <div>
          <label htmlFor="workbench-source" className="mb-2 block font-monosc text-[11px] uppercase tracking-wider text-[#9A9A90]">
            {props.workloadType === 'PATCH' ? 'candidate patch' : 'candidate source'}
          </label>
          <textarea
            id="workbench-source"
            value={props.source}
            onChange={event => props.onSourceChange(event.target.value)}
            rows={15}
            spellCheck={false}
            placeholder={props.workloadType === 'PATCH' ? '在这里粘贴 unified diff 补丁…' : '在这里写出完整可运行代码…'}
            className={`${wbInput} resize-y text-[13px] leading-6`}
          />
        </div>

        <div className="space-y-4">
          <div>
            <span className="mb-2 block text-xs font-medium text-[#C9C9C0]">工作负载</span>
            <div className="grid grid-cols-2 gap-2">
              {(['ALGORITHM', 'PATCH'] as const).map(type => (
                <button key={type} type="button" onClick={() => props.onWorkloadTypeChange(type)} className={wbSeg(props.workloadType === type)}>
                  {type === 'ALGORITHM' ? '算法题' : '项目补丁'}
                </button>
              ))}
            </div>
          </div>

          <label className="block">
            <span className="mb-2 block text-xs font-medium text-[#C9C9C0]">
              {props.workloadType === 'PATCH' ? '场景标识' : '题目标识'}
            </span>
            <input
              value={props.problemId}
              onChange={event => props.onProblemIdChange(event.target.value)}
              placeholder={props.workloadType === 'PATCH' ? '例如 scenario-1' : '例如 two-sum'}
              className={wbInput}
            />
          </label>

          {props.workloadType === 'ALGORITHM' && (
            <button
              type="button"
              onClick={props.onLoadProblem}
              disabled={props.problemLoading || !props.problemId.trim()}
              className="flex w-full items-center justify-center gap-2 rounded-[3px] border border-cinnabar/40 bg-cinnabar/10 px-3 py-2.5 text-xs font-semibold text-[#F2B4A6] transition-colors hover:bg-cinnabar/20 disabled:opacity-50"
            >
              {props.problemLoading ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <RefreshCw className="h-3.5 w-3.5" />}
              载入未考察变体
            </button>
          )}

          {props.problem && (
            <div className="rounded-[3px] border border-white/15 bg-white/5 p-3">
              <div className="flex items-center justify-between gap-2">
                <p className="text-xs font-semibold">{props.problem.title}</p>
                <span className="font-monosc text-[10px] text-cinnabar">{props.problem.difficulty}</span>
              </div>
              <p className="mt-2 whitespace-pre-wrap text-xs leading-5 text-[#9A9A90]">{props.problem.statement}</p>
              <pre className="mt-3 overflow-x-auto rounded-[3px] bg-black/30 p-2 text-[10px] leading-4 text-[#6E7A78]">{props.problem.sampleCases}</pre>
            </div>
          )}

          <label className="block">
            <span className="mb-2 block text-xs font-medium text-[#C9C9C0]">语言</span>
            <select
              value={props.language}
              onChange={event => props.onLanguageChange(event.target.value as SandboxLanguage)}
              className={wbInput}
            >
              <option value="JAVA">Java</option>
              <option value="PYTHON">Python</option>
              <option value="CPP">C++</option>
            </select>
          </label>

          {props.workloadType === 'ALGORITHM' && (
            <div>
              <span className="mb-2 block text-xs font-medium text-[#C9C9C0]">运行范围</span>
              <div className="grid grid-cols-2 gap-2">
                {(['SAMPLE', 'FULL'] as const).map(mode => (
                  <button key={mode} type="button" onClick={() => props.onRunModeChange(mode)} className={wbSeg(props.runMode === mode)}>
                    {mode === 'SAMPLE' ? '公开样例' : '完整判题'}
                  </button>
                ))}
              </div>
            </div>
          )}

          {props.submission && <JudgeResult execution={props.submission} />}

          {props.error && (
            <p role="alert" className="rounded-[3px] border border-cinnabar/40 bg-cinnabar/10 px-3 py-2 text-xs leading-5 text-[#F2B4A6]">
              {props.error}
            </p>
          )}

          <button
            type="button"
            onClick={props.onRun}
            disabled={running || !props.problemId.trim() || !props.source.trim()}
            className="inline-flex w-full items-center justify-center gap-2 rounded-[3px] bg-cinnabar px-4 py-3 text-sm font-bold text-white transition-colors hover:bg-cinnabar-deep disabled:cursor-not-allowed disabled:opacity-40"
          >
            {running ? <Clock3 className="h-4 w-4 animate-pulse" /> : <Play className="h-4 w-4" />}
            {running
              ? '判题进行中，可继续回答'
              : props.workloadType === 'PATCH'
                ? '提交补丁验证'
                : props.runMode === 'SAMPLE' ? '运行公开样例' : '提交完整判题'}
          </button>
        </div>
      </div>
    </section>
  );
}

function JudgeResult({ execution }: { execution: SandboxExecution }) {
  const verdictColor = execution.verdict === 'AC'
    ? 'text-[#5FBF8F]'
    : execution.verdict
      ? 'text-[#D9A441]'
      : 'text-[#9A9A90]';
  return (
    <div className="rounded-[3px] border border-white/15 bg-white/5 p-3 font-monosc text-[11px] leading-5 text-[#9A9A90]">
      <div className="flex items-center justify-between">
        <span>#{execution.submissionSeq}</span>
        <span className={verdictColor}>{execution.verdict ?? execution.status}</span>
      </div>
      {execution.total !== null && <p className="mt-2">cases {execution.passed}/{execution.total}</p>}
      {execution.timeMs !== null && <p>time {execution.timeMs} ms · memory {execution.memoryKb} KB</p>}
      {execution.firstFailedCase !== null && <p>first failed case #{execution.firstFailedCase}</p>}
      {execution.policyViolation && <p className="mt-2 text-[#E8856F]">沙箱策略已阻止：{execution.policyViolation}</p>}
      {execution.status === 'TIMEOUT_QUEUED' && <p className="mt-2 text-[#D9A441]">判题暂不可用，面试将改为代码走读。</p>}
    </div>
  );
}
