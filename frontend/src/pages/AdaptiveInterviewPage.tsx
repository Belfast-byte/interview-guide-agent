import { useCallback, useEffect, useMemo, useState } from 'react';
import { motion, useReducedMotion } from 'framer-motion';
import {
  AlertCircle,
  ArrowLeft,
  BrainCircuit,
  Check,
  CheckCircle2,
  ChevronRight,
  Clock3,
  Code2,
  FileText,
  Loader2,
  RefreshCw,
  Play,
  Send,
  Sparkles,
  Target,
  UserRound,
} from 'lucide-react';
import { useNavigate, useParams } from 'react-router-dom';
import { adaptiveInterviewApi } from '../api/adaptiveInterview';
import { getErrorMessage } from '../api/request';
import { ROUTES } from '../constants/routes';
import type {
  AdaptiveAssessmentReport,
  AdaptiveInterviewDimension,
  AdaptiveInterviewSession,
  SandboxExecution,
  SandboxLanguage,
  SandboxRunMode,
  ToolResultFollowUp,
} from '../types/adaptiveInterview';

const DEPTH_LABELS = {
  L0: '尚无证据',
  L1: '概念识别',
  L2: '实际应用',
  L3: '权衡分析',
  L4: '迁移洞察',
} as const;

export default function AdaptiveInterviewPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const reduceMotion = useReducedMotion();
  const [session, setSession] = useState<AdaptiveInterviewSession | null>(null);
  const [report, setReport] = useState<AdaptiveAssessmentReport | null>(null);
  const [candidateId, setCandidateId] = useState('');
  const [jd, setJd] = useState('');
  const [resume, setResume] = useState('');
  const [llmProvider, setLlmProvider] = useState('');
  const [answer, setAnswer] = useState('');
  const [loading, setLoading] = useState(Boolean(sessionId));
  const [working, setWorking] = useState(false);
  const [reportLoading, setReportLoading] = useState(false);
  const [error, setError] = useState('');
  const [reportError, setReportError] = useState('');
  const [problemId, setProblemId] = useState('');
  const [language, setLanguage] = useState<SandboxLanguage>('JAVA');
  const [runMode, setRunMode] = useState<SandboxRunMode>('SAMPLE');
  const [source, setSource] = useState('');
  const [submission, setSubmission] = useState<SandboxExecution | null>(null);
  const [followUps, setFollowUps] = useState<ToolResultFollowUp[]>([]);
  const [judging, setJudging] = useState(false);
  const [judgeError, setJudgeError] = useState('');

  const loadReport = useCallback(async (id: string) => {
    setReportLoading(true);
    setReportError('');
    try {
      setReport(await adaptiveInterviewApi.getReport(id));
    } catch (requestError) {
      setReportError(getErrorMessage(requestError));
    } finally {
      setReportLoading(false);
    }
  }, []);

  const loadSession = useCallback(async (id: string) => {
    setLoading(true);
    setError('');
    try {
      const loaded = await adaptiveInterviewApi.get(id);
      setSession(loaded);
      if (loaded.status === 'COMPLETED') {
        await loadReport(id);
      }
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }, [loadReport]);

  useEffect(() => {
    if (sessionId) {
      void loadSession(sessionId);
    }
  }, [loadSession, sessionId]);

  const completedTurns = useMemo(
    () => session?.turns.filter(turn => turn.answer !== null).length ?? 0,
    [session?.turns],
  );
  const currentDimension = useMemo(
    () => session?.dimensions.find(dimension => dimension.status === 'IN_PROGRESS'),
    [session?.dimensions],
  );
  const algorithmActive = Boolean(
    currentDimension && /算法|数据结构|algorithm/i.test(`${currentDimension.dimension} ${currentDimension.focus}`),
  );

  useEffect(() => {
    if (!session || !submission || !['PENDING', 'RUNNING'].includes(submission.status)) return;
    const timer = window.setTimeout(async () => {
      try {
        const updated = await adaptiveInterviewApi.getCodeSubmission(
          session.sessionId,
          submission.submissionId,
        );
        setSubmission(updated);
      } catch (requestError) {
        setJudgeError(getErrorMessage(requestError));
      }
    }, 2_000);
    return () => window.clearTimeout(timer);
  }, [session, submission]);

  useEffect(() => {
    if (!session || !submission || session.status !== 'IN_PROGRESS') return;
    if (['PENDING', 'RUNNING'].includes(submission.status)) return;
    if (followUps.some(item => item.resultId === submission.submissionId)) return;
    const timer = window.setTimeout(async () => {
      try {
        setFollowUps(await adaptiveInterviewApi.getToolResultFollowUps(session.sessionId));
      } catch (requestError) {
        setJudgeError(getErrorMessage(requestError));
      }
    }, 2_000);
    return () => window.clearTimeout(timer);
  }, [followUps, session, submission]);

  const createInterview = async () => {
    const request = {
      candidateId: candidateId.trim(),
      jd: jd.trim(),
      resume: resume.trim(),
      ...(llmProvider.trim() ? { llmProvider: llmProvider.trim() } : {}),
    };
    if (!request.candidateId || !request.jd || !request.resume) {
      setError('请填写候选人标识、职位描述和简历内容。');
      return;
    }

    setWorking(true);
    setError('');
    try {
      const created = await adaptiveInterviewApi.create(request);
      setSession(created);
      navigate(ROUTES.adaptiveInterviewSession(created.sessionId));
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setWorking(false);
    }
  };

  const submitAnswer = async (activeSession: AdaptiveInterviewSession) => {
    const content = answer.trim();
    if (!content) {
      setError('回答不能为空。请说明你的判断、做法和取舍。');
      return;
    }

    setWorking(true);
    setError('');
    try {
      const updated = await adaptiveInterviewApi.submitAnswer(activeSession.sessionId, {
        turnIndex: activeSession.currentTurn,
        answer: content,
      });
      setSession(updated);
      setAnswer('');
      if (updated.status === 'COMPLETED') {
        await loadReport(updated.sessionId);
      }
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setWorking(false);
    }
  };

  const submitCode = async (activeSession: AdaptiveInterviewSession) => {
    if (!problemId.trim() || !source.trim()) {
      setJudgeError('请填写题目标识和代码后再运行。');
      return;
    }
    setJudging(true);
    setJudgeError('');
    try {
      const turnIndex = activeSession.currentTurn;
      if (runMode === 'SAMPLE') {
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
            problemId: problemId.trim(),
            language,
            runMode,
          },
        });
        setSession(updated);
        setSubmission(await adaptiveInterviewApi.getLatestCodeSubmission(
          activeSession.sessionId,
          turnIndex,
        ));
        if (updated.status === 'COMPLETED') {
          await loadReport(updated.sessionId);
        }
      }
    } catch (requestError) {
      setJudgeError(getErrorMessage(requestError));
    } finally {
      setJudging(false);
    }
  };

  const startNew = () => {
    setSession(null);
    setReport(null);
    setAnswer('');
    setError('');
    setReportError('');
    navigate(ROUTES.adaptiveInterview);
  };

  if (loading && !session) {
    return <LoadingState />;
  }

  if (!session) {
    return (
      <SetupView
        candidateId={candidateId}
        jd={jd}
        resume={resume}
        llmProvider={llmProvider}
        working={working}
        error={error}
        hasSessionId={Boolean(sessionId)}
        onCandidateIdChange={setCandidateId}
        onJdChange={setJd}
        onResumeChange={setResume}
        onProviderChange={setLlmProvider}
        onCreate={() => void createInterview()}
        onRetry={() => void loadSession(sessionId!)}
      />
    );
  }

  return (
    <div className="mx-auto max-w-7xl pb-12">
      <header className="mb-6 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <button
            type="button"
            onClick={startNew}
            className="mb-4 inline-flex items-center gap-2 rounded-lg text-sm font-medium text-slate-500 transition-colors hover:text-slate-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 dark:text-slate-400 dark:hover:text-white"
          >
            <ArrowLeft className="h-4 w-4" />
            新面试
          </button>
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-primary-600 text-white shadow-lg shadow-primary-500/25">
              <BrainCircuit className="h-5 w-5" />
            </div>
            <div>
              <p className="font-mono text-[10px] uppercase tracking-[0.18em] text-primary-500">
                {session.runtimeVersion}
              </p>
              <h1 className="text-2xl font-bold text-slate-950 dark:text-white">自适应技术面试</h1>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <span className="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-600 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300">
            {session.status === 'COMPLETED' ? '已完成' : `第 ${session.currentTurn} / ${session.maxTurns} 轮`}
          </span>
          <button
            type="button"
            onClick={() => void loadSession(session.sessionId)}
            disabled={loading || working}
            className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-600 transition-colors hover:border-primary-300 hover:text-primary-600 disabled:opacity-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            刷新
          </button>
        </div>
      </header>

      {error && <ErrorBanner message={error} />}

      <DimensionRail dimensions={session.dimensions} />

      <div className="mt-6 grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_340px]">
        <section className="overflow-hidden rounded-3xl border border-slate-200/80 bg-white/90 shadow-xl shadow-slate-200/40 dark:border-slate-700/80 dark:bg-slate-800/85 dark:shadow-slate-950/20">
          <div className="border-b border-slate-100 px-5 py-5 dark:border-slate-700 sm:px-7">
            <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary-500">Interview transcript</p>
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">已完成 {completedTurns} 轮，问题与回答均作为评估证据保存。</p>
          </div>
          <div className="space-y-6 px-5 py-6 sm:px-7">
            {session.turns.map(turn => (
              <motion.article
                key={turn.turnIndex}
                initial={reduceMotion ? false : { opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className="space-y-3"
              >
                <div className="flex gap-3">
                  <div className="flex h-9 w-9 flex-none items-center justify-center rounded-xl bg-primary-50 text-primary-600 dark:bg-primary-900/30 dark:text-primary-300">
                    <BrainCircuit className="h-4 w-4" />
                  </div>
                  <div className="min-w-0 flex-1 rounded-2xl rounded-tl-md border border-primary-100 bg-primary-50/60 px-4 py-3.5 dark:border-primary-900/60 dark:bg-primary-950/20">
                    <p className="mb-1 font-mono text-[10px] uppercase tracking-[0.14em] text-primary-500">Question {turn.turnIndex}</p>
                    <p className="whitespace-pre-wrap text-sm font-medium leading-7 text-slate-900 dark:text-slate-100">{turn.question}</p>
                  </div>
                </div>
                {turn.answer !== null && (
                  <div className="ml-8 rounded-2xl rounded-tr-md border border-slate-200 bg-white px-4 py-3.5 dark:border-slate-700 dark:bg-slate-800">
                    <p className="mb-1 font-mono text-[10px] uppercase tracking-[0.14em] text-slate-400">Your answer</p>
                    <p className="whitespace-pre-wrap text-sm leading-6 text-slate-700 dark:text-slate-300">{turn.answer}</p>
                  </div>
                )}
              </motion.article>
            ))}
            {followUps.filter(item => item.turnIndex === session.currentTurn).map(item => (
              <div key={item.resultId} className="flex gap-3">
                <div className="flex h-9 w-9 flex-none items-center justify-center rounded-xl bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300">
                  <Code2 className="h-4 w-4" />
                </div>
                <div className="min-w-0 flex-1 rounded-2xl rounded-tl-md border border-amber-200 bg-amber-50/70 px-4 py-3.5 dark:border-amber-800/60 dark:bg-amber-950/20">
                  <p className="mb-1 font-mono text-[10px] uppercase tracking-[0.14em] text-amber-600">Judge follow-up</p>
                  <p className="text-sm font-medium leading-7 text-slate-900 dark:text-slate-100">{item.responseContent}</p>
                </div>
              </div>
            ))}
          </div>

          {session.status === 'IN_PROGRESS' && algorithmActive && (
            <AlgorithmWorkbench
              problemId={problemId}
              language={language}
              runMode={runMode}
              source={source}
              submission={submission}
              judging={judging}
              error={judgeError}
              onProblemIdChange={setProblemId}
              onLanguageChange={setLanguage}
              onRunModeChange={setRunMode}
              onSourceChange={setSource}
              onRun={() => void submitCode(session)}
            />
          )}

          {session.status === 'IN_PROGRESS' && (
            <div className="border-t border-slate-100 bg-slate-50/80 px-5 py-5 dark:border-slate-700 dark:bg-slate-900/35 sm:px-7">
              <label htmlFor="adaptive-answer" className="mb-2 block text-sm font-semibold text-slate-800 dark:text-slate-100">你的回答</label>
              <textarea
                id="adaptive-answer"
                value={answer}
                onChange={event => setAnswer(event.target.value)}
                onKeyDown={event => {
                  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
                    event.preventDefault();
                    void submitAnswer(session);
                  }
                }}
                rows={7}
                disabled={working}
                placeholder="说明判断依据、实施方式、边界条件和取舍。"
                className="dark-input w-full resize-y rounded-2xl px-4 py-3.5 text-sm leading-6 outline-none disabled:opacity-60"
              />
              <div className="mt-3 flex flex-col-reverse gap-3 sm:flex-row sm:items-center sm:justify-between">
                <p className="text-xs text-slate-400">Ctrl / ⌘ + Enter 提交</p>
                <button
                  type="button"
                  onClick={() => void submitAnswer(session)}
                  disabled={working || !answer.trim()}
                  className="btn-primary inline-flex items-center justify-center gap-2 rounded-xl px-5 py-2.5 text-sm disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {working ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
                  {working ? '正在评估并生成下一题' : '提交回答'}
                </button>
              </div>
            </div>
          )}
        </section>

        <aside className="space-y-4 xl:sticky xl:top-8">
          {session.status === 'COMPLETED' ? (
            <ReportPanel
              report={report}
              loading={reportLoading}
              error={reportError}
              onRetry={() => void loadReport(session.sessionId)}
            />
          ) : (
            <CurrentFocus dimensions={session.dimensions} />
          )}
        </aside>
      </div>
    </div>
  );
}

interface AlgorithmWorkbenchProps {
  problemId: string;
  language: SandboxLanguage;
  runMode: SandboxRunMode;
  source: string;
  submission: SandboxExecution | null;
  judging: boolean;
  error: string;
  onProblemIdChange: (value: string) => void;
  onLanguageChange: (value: SandboxLanguage) => void;
  onRunModeChange: (value: SandboxRunMode) => void;
  onSourceChange: (value: string) => void;
  onRun: () => void;
}

function AlgorithmWorkbench(props: AlgorithmWorkbenchProps) {
  const running = props.judging || props.submission?.status === 'PENDING' || props.submission?.status === 'RUNNING';
  return (
    <section className="border-t border-slate-200 bg-slate-950 text-slate-100 dark:border-slate-700">
      <div className="flex flex-col gap-4 border-b border-slate-800 px-5 py-4 sm:px-7 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl border border-slate-700 bg-slate-900 text-cyan-300"><Code2 className="h-4 w-4" /></div>
          <div>
            <p className="font-mono text-[10px] uppercase tracking-[0.18em] text-cyan-300">Judge docket</p>
            <h2 className="text-sm font-semibold">代码判题工单</h2>
          </div>
        </div>
        <div className="flex items-center gap-2 font-mono text-[10px] uppercase tracking-wider text-slate-400">
          {['PENDING', 'RUNNING', 'DONE'].map((status, index) => {
            const active = props.submission && (
              props.submission.status === status
              || (status === 'DONE' && props.submission.status === 'TIMEOUT_QUEUED')
            );
            return <span key={status} className={`rounded-full border px-2.5 py-1 ${active ? 'border-cyan-400/60 bg-cyan-400/10 text-cyan-200' : 'border-slate-700'}`}>{index + 1}. {status}</span>;
          })}
        </div>
      </div>
      <div className="grid gap-4 px-5 py-5 sm:px-7 lg:grid-cols-[minmax(0,1fr)_220px]">
        <div>
          <label htmlFor="algorithm-source" className="mb-2 block font-mono text-[11px] uppercase tracking-wider text-slate-400">candidate source</label>
          <textarea
            id="algorithm-source"
            value={props.source}
            onChange={event => props.onSourceChange(event.target.value)}
            rows={15}
            spellCheck={false}
            placeholder="在这里写出完整可运行代码…"
            className="w-full resize-y rounded-xl border border-slate-700 bg-slate-900 px-4 py-3 font-mono text-[13px] leading-6 text-slate-100 outline-none transition focus:border-cyan-400 focus:ring-2 focus:ring-cyan-400/15"
          />
        </div>
        <div className="space-y-4">
          <label className="block"><span className="mb-2 block text-xs font-medium text-slate-300">题目标识</span><input value={props.problemId} onChange={event => props.onProblemIdChange(event.target.value)} placeholder="例如 two-sum" className="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2.5 font-mono text-xs outline-none focus:border-cyan-400" /></label>
          <label className="block"><span className="mb-2 block text-xs font-medium text-slate-300">语言</span><select value={props.language} onChange={event => props.onLanguageChange(event.target.value as SandboxLanguage)} className="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2.5 text-xs outline-none focus:border-cyan-400"><option value="JAVA">Java</option><option value="PYTHON">Python</option><option value="CPP">C++</option></select></label>
          <div><span className="mb-2 block text-xs font-medium text-slate-300">运行范围</span><div className="grid grid-cols-2 gap-2">{(['SAMPLE', 'FULL'] as const).map(mode => <button key={mode} type="button" onClick={() => props.onRunModeChange(mode)} className={`rounded-lg border px-3 py-2 text-xs font-semibold ${props.runMode === mode ? 'border-cyan-400 bg-cyan-400/10 text-cyan-200' : 'border-slate-700 text-slate-400'}`}>{mode === 'SAMPLE' ? '公开样例' : '完整判题'}</button>)}</div></div>
          {props.submission && <JudgeResult execution={props.submission} />}
          {props.error && <p role="alert" className="rounded-lg border border-red-800 bg-red-950/50 px-3 py-2 text-xs leading-5 text-red-200">{props.error}</p>}
          <button type="button" onClick={props.onRun} disabled={running || !props.problemId.trim() || !props.source.trim()} className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-cyan-300 px-4 py-3 text-sm font-bold text-slate-950 transition hover:bg-cyan-200 disabled:cursor-not-allowed disabled:opacity-45">
            {running ? <Clock3 className="h-4 w-4 animate-pulse" /> : <Play className="h-4 w-4" />}
            {running ? '判题进行中，可继续回答' : props.runMode === 'SAMPLE' ? '运行公开样例' : '提交完整判题'}
          </button>
        </div>
      </div>
    </section>
  );
}

function JudgeResult({ execution }: { execution: SandboxExecution }) {
  const verdictTone = execution.verdict === 'AC' ? 'text-emerald-300' : execution.verdict ? 'text-amber-300' : 'text-cyan-300';
  return (
    <div className="rounded-lg border border-slate-700 bg-slate-900 p-3 font-mono text-[11px] leading-5 text-slate-400">
      <div className="flex items-center justify-between"><span>#{execution.submissionSeq}</span><span className={verdictTone}>{execution.verdict ?? execution.status}</span></div>
      {execution.total !== null && <p className="mt-2">cases {execution.passed}/{execution.total}</p>}
      {execution.timeMs !== null && <p>time {execution.timeMs} ms · memory {execution.memoryKb} KB</p>}
      {execution.firstFailedCase !== null && <p>first failed case #{execution.firstFailedCase}</p>}
      {execution.pendingRejudge && <p className="mt-2 text-amber-300">平台故障，已进入待重判；不计入能力证据。</p>}
      {execution.status === 'TIMEOUT_QUEUED' && <p className="mt-2 text-amber-300">判题暂不可用，面试将改为代码走读。</p>}
    </div>
  );
}

interface SetupViewProps {
  candidateId: string;
  jd: string;
  resume: string;
  llmProvider: string;
  working: boolean;
  error: string;
  hasSessionId: boolean;
  onCandidateIdChange: (value: string) => void;
  onJdChange: (value: string) => void;
  onResumeChange: (value: string) => void;
  onProviderChange: (value: string) => void;
  onCreate: () => void;
  onRetry: () => void;
}

function SetupView(props: SetupViewProps) {
  return (
    <div className="mx-auto max-w-6xl pb-12">
      <header className="relative mb-6 overflow-hidden rounded-3xl border border-primary-100 bg-white/90 px-6 py-9 shadow-xl shadow-primary-100/40 dark:border-primary-900/50 dark:bg-slate-800/85 dark:shadow-slate-950/20 sm:px-9">
        <div className="absolute -right-10 -top-20 h-64 w-64 rounded-full bg-primary-200/45 blur-3xl dark:bg-primary-800/15" />
        <div className="relative max-w-3xl">
          <div className="mb-5 inline-flex items-center gap-2 rounded-full bg-primary-50 px-3 py-1 text-xs font-semibold text-primary-600 dark:bg-primary-900/30 dark:text-primary-300">
            <Sparkles className="h-3.5 w-3.5" />
            ADAPTIVE INTERVIEW · M0–M5
          </div>
          <h1 className="text-3xl font-bold leading-tight text-slate-950 dark:text-white sm:text-4xl">不是固定题单，<br />而是一条有证据的追问路径。</h1>
          <p className="mt-4 max-w-2xl text-sm leading-7 text-slate-600 dark:text-slate-300 sm:text-base">系统根据职位与简历规划维度，每轮只评估当前回答，最终报告中的每个结论都能回到原始问答。</p>
        </div>
      </header>

      {props.error && (
        <ErrorBanner
          message={props.error}
          action={props.hasSessionId ? { label: '重新读取', run: props.onRetry } : undefined}
        />
      )}

      <section className="overflow-hidden rounded-3xl border border-slate-200 bg-white/90 shadow-xl shadow-slate-200/35 dark:border-slate-700 dark:bg-slate-800/85 dark:shadow-slate-950/20">
        <div className="grid gap-6 p-6 sm:p-8 lg:grid-cols-2">
          <div className="space-y-5">
            <TextField label="候选人标识" icon={UserRound} value={props.candidateId} onChange={props.onCandidateIdChange} placeholder="例如 candidate-2026-001" maxLength={64} />
            <TextField label="LLM Provider（可选）" icon={BrainCircuit} value={props.llmProvider} onChange={props.onProviderChange} placeholder="留空使用默认 Provider" maxLength={64} />
            <ContextField label="职位描述" icon={Target} value={props.jd} onChange={props.onJdChange} placeholder="岗位职责、技术栈、业务场景和候选人级别" />
          </div>
          <ContextField label="候选人简历" icon={FileText} value={props.resume} onChange={props.onResumeChange} placeholder="项目经历、技术能力、职责范围和代表性成果" tall />
        </div>
        <div className="flex flex-col gap-4 border-t border-slate-100 bg-slate-50/80 px-6 py-5 dark:border-slate-700 dark:bg-slate-900/35 sm:flex-row sm:items-center sm:justify-between sm:px-8">
          <p className="max-w-2xl text-xs leading-5 text-slate-500 dark:text-slate-400">创建前不会写入会话；规划或首题生成失败会直接返回错误，不留下半成品记录。</p>
          <button
            type="button"
            onClick={props.onCreate}
            disabled={props.working || !props.candidateId.trim() || !props.jd.trim() || !props.resume.trim()}
            className="btn-primary inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-xl px-6 py-3 text-sm disabled:cursor-not-allowed disabled:opacity-50"
          >
            {props.working ? <Loader2 className="h-4 w-4 animate-spin" /> : <BrainCircuit className="h-4 w-4" />}
            {props.working ? '正在规划并生成首题' : '开始自适应面试'}
          </button>
        </div>
      </section>
    </div>
  );
}

function DimensionRail({ dimensions }: { dimensions: AdaptiveInterviewDimension[] }) {
  return (
    <ol className="grid overflow-hidden rounded-2xl border border-slate-200 bg-white/85 dark:border-slate-700 dark:bg-slate-800/80 md:grid-flow-col md:auto-cols-fr">
      {dimensions.map((dimension, index) => (
        <li key={dimension.order} className="relative flex min-w-0 items-center gap-3 border-b border-slate-100 px-4 py-4 last:border-0 dark:border-slate-700 md:border-b-0 md:border-r">
          <span className={`flex h-8 w-8 flex-none items-center justify-center rounded-full text-xs font-bold ${dimension.status === 'COMPLETED' ? 'bg-emerald-500 text-white' : dimension.status === 'IN_PROGRESS' ? 'bg-primary-600 text-white ring-4 ring-primary-500/15' : 'bg-slate-100 text-slate-400 dark:bg-slate-700'}`}>
            {dimension.status === 'COMPLETED' ? <Check className="h-4 w-4" /> : dimension.order + 1}
          </span>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-slate-800 dark:text-slate-100">{dimension.dimension}</p>
            <p className="truncate text-xs text-slate-400">{dimension.completedTurns}/{dimension.allocatedTurns} · {dimension.focus}</p>
          </div>
          {index < dimensions.length - 1 && <ChevronRight className="absolute -right-2 z-10 hidden h-4 w-4 rounded-full bg-white text-slate-300 dark:bg-slate-800 md:block" />}
        </li>
      ))}
    </ol>
  );
}

function CurrentFocus({ dimensions }: { dimensions: AdaptiveInterviewDimension[] }) {
  const current = dimensions.find(dimension => dimension.status === 'IN_PROGRESS');
  return (
    <div className="rounded-2xl border border-slate-200 bg-white/90 p-5 dark:border-slate-700 dark:bg-slate-800/85">
      <div className="mb-4 flex items-center gap-2"><Target className="h-4 w-4 text-primary-500" /><h2 className="text-sm font-bold text-slate-900 dark:text-white">当前考察重点</h2></div>
      <p className="text-lg font-bold text-slate-900 dark:text-white">{current!.dimension}</p>
      <p className="mt-1 text-sm leading-6 text-slate-500 dark:text-slate-400">{current!.focus}</p>
      <div className="mt-5 h-1.5 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-700"><div className="h-full rounded-full bg-primary-500" style={{ width: `${current!.completedTurns / current!.allocatedTurns * 100}%` }} /></div>
    </div>
  );
}

function ReportPanel({ report, loading, error, onRetry }: { report: AdaptiveAssessmentReport | null; loading: boolean; error: string; onRetry: () => void }) {
  if (loading) return <LoadingCard label="正在组装可追溯报告" />;
  if (error) return <ErrorBanner message={error} action={{ label: '重新加载报告', run: onRetry }} />;
  return (
    <div className="space-y-4">
      <div className="rounded-2xl border border-emerald-200 bg-emerald-50/80 p-5 dark:border-emerald-800/60 dark:bg-emerald-900/15">
        <CheckCircle2 className="h-6 w-6 text-emerald-500" />
        <h2 className="mt-3 text-lg font-bold text-emerald-900 dark:text-emerald-200">面试已完成</h2>
        <p className="mt-1 text-xs leading-5 text-emerald-700 dark:text-emerald-300">报告由已验证的评估与证据确定性生成。</p>
      </div>
      {report!.dimensions.map(dimension => (
        <article key={dimension.order} className="rounded-2xl border border-slate-200 bg-white/90 p-5 dark:border-slate-700 dark:bg-slate-800/85">
          <div className="flex items-start justify-between gap-3">
            <div><h3 className="text-sm font-bold text-slate-900 dark:text-white">{dimension.dimension}</h3><p className="mt-1 text-xs text-slate-400">{dimension.focus}</p></div>
            <span className="rounded-lg bg-primary-50 px-2.5 py-1 font-mono text-xs font-bold text-primary-600 dark:bg-primary-900/30 dark:text-primary-300">{dimension.depthLevel}</span>
          </div>
          <p className="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-300">{DEPTH_LABELS[dimension.depthLevel]} · {dimension.rationale}</p>
          <div className="mt-3 space-y-2">{dimension.evidences.map((evidence, index) => <blockquote key={`${evidence.turnIndex}-${index}`} className="border-l-2 border-primary-300 pl-3 text-xs leading-5 text-slate-500 dark:text-slate-400">{evidence.quote ?? evidence.toolResult!.output}</blockquote>)}</div>
        </article>
      ))}
      {report!.practiceRecommendations.map(practice => (
        <article key={practice.questionSourceId} className="rounded-2xl border border-amber-200 bg-amber-50/70 p-5 dark:border-amber-800/60 dark:bg-amber-900/15">
          <p className="text-xs font-semibold text-amber-700 dark:text-amber-300">练习 · {practice.dimension} · {practice.questionDifficulty}</p>
          <p className="mt-2 text-sm font-medium leading-6 text-amber-950 dark:text-amber-100">{practice.question}</p>
        </article>
      ))}
    </div>
  );
}

function TextField({ label, icon: Icon, value, onChange, placeholder, maxLength }: { label: string; icon: typeof Target; value: string; onChange: (value: string) => void; placeholder: string; maxLength: number }) {
  return <label className="block"><span className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-800 dark:text-slate-100"><Icon className="h-4 w-4 text-primary-500" />{label}</span><input value={value} onChange={event => onChange(event.target.value)} placeholder={placeholder} maxLength={maxLength} className="dark-input w-full rounded-xl px-4 py-3 text-sm outline-none" /></label>;
}

function ContextField({ label, icon: Icon, value, onChange, placeholder, tall = false }: { label: string; icon: typeof Target; value: string; onChange: (value: string) => void; placeholder: string; tall?: boolean }) {
  return <label className="block"><span className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-800 dark:text-slate-100"><Icon className="h-4 w-4 text-primary-500" />{label}</span><textarea value={value} onChange={event => onChange(event.target.value)} placeholder={placeholder} rows={tall ? 18 : 9} className="dark-input w-full resize-y rounded-2xl px-4 py-3.5 text-sm leading-6 outline-none" /></label>;
}

function ErrorBanner({ message, action }: { message: string; action?: { label: string; run: () => void } }) {
  return <div role="alert" className="mb-5 flex items-start gap-3 rounded-2xl border border-red-200 bg-red-50 px-4 py-3.5 text-red-800 dark:border-red-800/60 dark:bg-red-900/20 dark:text-red-200"><AlertCircle className="mt-0.5 h-4 w-4 flex-none" /><div className="min-w-0 flex-1"><p className="text-sm font-semibold">请求未完成</p><p className="mt-0.5 break-words text-xs leading-5">{message}</p></div>{action && <button type="button" onClick={action.run} className="rounded text-xs font-semibold hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-red-500">{action.label}</button>}</div>;
}

function LoadingState() { return <div className="flex min-h-[55vh] items-center justify-center"><LoadingCard label="正在读取面试快照" /></div>; }
function LoadingCard({ label }: { label: string }) { return <div className="rounded-2xl border border-slate-200 bg-white/90 p-6 text-center dark:border-slate-700 dark:bg-slate-800/85"><Loader2 className="mx-auto h-6 w-6 animate-spin text-primary-500" /><p className="mt-3 text-sm text-slate-500 dark:text-slate-400">{label}</p></div>; }
