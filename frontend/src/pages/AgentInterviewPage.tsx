import { useCallback, useEffect, useMemo, useState } from 'react';
import { motion, useReducedMotion } from 'framer-motion';
import {
  AlertTriangle,
  ArrowLeft,
  Bot,
  Check,
  CheckCircle2,
  Circle,
  Clock3,
  Copy,
  FileText,
  Loader2,
  Play,
  RefreshCw,
  RotateCcw,
  Send,
  ShieldCheck,
  Sparkles,
  SquareTerminal,
  Target,
  WandSparkles,
} from 'lucide-react';
import { useNavigate, useParams } from 'react-router-dom';
import { agentInterviewApi } from '../api/agentInterview';
import { getErrorMessage } from '../api/request';
import { ROUTES } from '../constants/routes';
import type {
  AgentInterviewSession,
  AgentInterviewStatus,
  AgentInterviewTurn,
} from '../types/agentInterview';

const JD_EXAMPLE = `招聘 Java 后端工程师，要求熟悉 Spring Boot、PostgreSQL、Redis，理解事务、缓存一致性、并发控制和分布式系统。候选人需要能够解释真实项目中的架构取舍和故障处理过程。`;

const RESUME_EXAMPLE = `候选人有三年 Java 开发经验，使用过 Spring Boot、Redis 和 MySQL，负责过订单系统。项目中使用延迟双删处理缓存一致性，通过消息队列执行补偿任务，并使用业务唯一键保证消费者幂等。`;

const STATUS_META: Record<AgentInterviewStatus, { label: string; className: string }> = {
  CREATED: {
    label: '正在启动',
    className: 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-900/20 dark:text-amber-300 dark:border-amber-800/60',
  },
  IN_PROGRESS: {
    label: '面试进行中',
    className: 'bg-cyan-50 text-cyan-700 border-cyan-200 dark:bg-cyan-900/20 dark:text-cyan-300 dark:border-cyan-800/60',
  },
  COMPLETED: {
    label: '已完成',
    className: 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-900/20 dark:text-emerald-300 dark:border-emerald-800/60',
  },
  FAILED: {
    label: '运行失败',
    className: 'bg-red-50 text-red-700 border-red-200 dark:bg-red-900/20 dark:text-red-300 dark:border-red-800/60',
  },
};

export default function AgentInterviewPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const reduceMotion = useReducedMotion();
  const [jd, setJd] = useState('');
  const [resume, setResume] = useState('');
  const [answer, setAnswer] = useState('');
  const [session, setSession] = useState<AgentInterviewSession | null>(null);
  const [loadingSession, setLoadingSession] = useState(false);
  const [creating, setCreating] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);

  const loadSession = useCallback(async (id: string) => {
    setLoadingSession(true);
    setError('');
    try {
      const loaded = await agentInterviewApi.getSession(id);
      setSession(loaded);
    } catch (loadError) {
      setError(getErrorMessage(loadError));
    } finally {
      setLoadingSession(false);
    }
  }, []);

  useEffect(() => {
    if (!sessionId) {
      setSession(null);
      setLoadingSession(false);
      return;
    }
    if (session?.sessionId === sessionId) {
      return;
    }
    void loadSession(sessionId);
  }, [loadSession, session?.sessionId, sessionId]);

  const answeredTurns = useMemo(
    () => session?.turns.filter(turn => turn.answer !== null).length ?? 0,
    [session?.turns],
  );

  const fillExample = () => {
    setJd(JD_EXAMPLE);
    setResume(RESUME_EXAMPLE);
    setError('');
  };

  const createSession = async () => {
    const normalizedJd = jd.trim();
    const normalizedResume = resume.trim();
    if (!normalizedJd || !normalizedResume) {
      setError('请先填写职位描述和简历内容，再启动面试。');
      return;
    }

    setCreating(true);
    setError('');
    try {
      const created = await agentInterviewApi.createSession({
        jd: normalizedJd,
        resume: normalizedResume,
      });
      setSession(created);
      setAnswer('');
      navigate(ROUTES.agentInterviewSession(created.sessionId));
    } catch (createError) {
      setError(getErrorMessage(createError));
    } finally {
      setCreating(false);
    }
  };

  const submitAnswer = async () => {
    if (!session || session.status !== 'IN_PROGRESS') {
      return;
    }
    const normalizedAnswer = answer.trim();
    if (!normalizedAnswer) {
      setError('回答不能为空。请说明你的判断、做法或取舍。');
      return;
    }

    setSubmitting(true);
    setError('');
    try {
      const updated = await agentInterviewApi.submitAnswer(session.sessionId, {
        answer: normalizedAnswer,
      });
      setSession(updated);
      setAnswer('');
    } catch (submitError) {
      setError(getErrorMessage(submitError));
    } finally {
      setSubmitting(false);
    }
  };

  const startNewSession = () => {
    setSession(null);
    setAnswer('');
    setError('');
    navigate(ROUTES.agentInterview);
  };

  const copyHash = async () => {
    if (!session?.selectedSkillHash) {
      return;
    }
    await navigator.clipboard.writeText(session.selectedSkillHash);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1600);
  };

  if (sessionId && loadingSession && !session) {
    return <PageLoading />;
  }

  if (!session) {
    return (
      <SetupView
        jd={jd}
        resume={resume}
        creating={creating}
        error={error}
        onJdChange={setJd}
        onResumeChange={setResume}
        onFillExample={fillExample}
        onCreate={createSession}
        onRetrySession={sessionId ? () => void loadSession(sessionId) : undefined}
      />
    );
  }

  const statusMeta = STATUS_META[session.status];

  return (
    <div className="max-w-7xl mx-auto pb-12">
      <header className="mb-7">
        <div className="flex flex-wrap items-center gap-3 mb-4">
          <button
            type="button"
            onClick={startNewSession}
            className="inline-flex items-center gap-2 text-sm font-medium text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 rounded-lg"
          >
            <ArrowLeft className="w-4 h-4" />
            新面试
          </button>
          <span className="h-4 w-px bg-slate-200 dark:bg-slate-700" />
          <span className="font-mono text-[11px] uppercase tracking-[0.18em] text-slate-400 dark:text-slate-500">
            {session.runtimeVersion}
          </span>
        </div>

        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <div className="flex items-center gap-3 mb-2">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary-500 to-cyan-500 text-white flex items-center justify-center shadow-lg shadow-primary-500/20">
                <Bot className="w-5 h-5" />
              </div>
              <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 dark:text-white">
                有界 Agent 面试
              </h1>
            </div>
            <p className="text-sm text-slate-500 dark:text-slate-400 max-w-2xl">
              模型读取显式上下文，自主选择一个 Skill，并根据每轮回答继续追问。
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <span className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-full border text-xs font-semibold ${statusMeta.className}`}>
              <span className={`w-1.5 h-1.5 rounded-full ${session.status === 'IN_PROGRESS' ? 'bg-cyan-500 animate-pulse' : 'bg-current'}`} />
              {statusMeta.label}
            </span>
            <button
              type="button"
              onClick={() => void loadSession(session.sessionId)}
              disabled={loadingSession || submitting}
              className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full border border-slate-200 dark:border-slate-700 text-xs font-semibold text-slate-600 dark:text-slate-300 hover:bg-white dark:hover:bg-slate-800 disabled:opacity-50 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${loadingSession ? 'animate-spin' : ''}`} />
              刷新状态
            </button>
          </div>
        </div>
      </header>

      {error && <ErrorBanner message={error} />}

      <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_320px] gap-6 items-start">
        <section className="min-w-0 rounded-3xl border border-slate-200/80 dark:border-slate-700/80 bg-white/90 dark:bg-slate-800/80 shadow-xl shadow-slate-200/35 dark:shadow-slate-950/20 overflow-hidden">
          <div className="px-5 sm:px-7 py-5 border-b border-slate-100 dark:border-slate-700/80 flex items-center justify-between gap-4">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary-500">Interview transcript</p>
              <h2 className="text-lg font-bold text-slate-900 dark:text-white mt-1">
                第 {session.currentTurn} / {session.maxTurns} 轮
              </h2>
            </div>
            <div className="text-right">
              <p className="text-xs text-slate-400">已回答</p>
              <p className="font-mono text-lg font-semibold text-slate-700 dark:text-slate-200">
                {String(answeredTurns).padStart(2, '0')}
              </p>
            </div>
          </div>

          <div className="px-5 sm:px-7 py-6 space-y-6">
            {session.turns.map((turn) => (
              <TurnConversation
                key={turn.turnNumber}
                turn={turn}
                active={turn.turnNumber === session.currentTurn && turn.answer === null}
                reduceMotion={Boolean(reduceMotion)}
              />
            ))}

            {session.status === 'COMPLETED' && (
              <CompletionPanel
                reason={session.finishReason || '已完成全部面试轮次'}
                onRestart={startNewSession}
              />
            )}

            {session.status === 'FAILED' && (
              <FailurePanel
                reason={session.finishReason || 'Agent 未能在预算内产生有效动作'}
                onRestart={startNewSession}
              />
            )}
          </div>

          {session.status === 'IN_PROGRESS' && session.currentQuestion && (
            <div className="px-5 sm:px-7 py-5 bg-slate-50/80 dark:bg-slate-900/35 border-t border-slate-100 dark:border-slate-700/80">
              <label htmlFor="agent-answer" className="block text-sm font-semibold text-slate-800 dark:text-slate-100 mb-2">
                你的回答
              </label>
              <textarea
                id="agent-answer"
                value={answer}
                onChange={(event) => setAnswer(event.target.value)}
                onKeyDown={(event) => {
                  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
                    event.preventDefault();
                    void submitAnswer();
                  }
                }}
                maxLength={20_000}
                rows={7}
                placeholder="说明你的判断依据、具体做法和取舍。模型会根据这段回答继续追问。"
                disabled={submitting}
                className="dark-input w-full rounded-2xl px-4 py-3.5 text-sm leading-6 resize-y outline-none placeholder:text-slate-400 disabled:opacity-60"
              />
              <div className="mt-3 flex flex-col-reverse sm:flex-row sm:items-center sm:justify-between gap-3">
                <p className="text-xs text-slate-400 dark:text-slate-500">
                  Ctrl / ⌘ + Enter 提交 · {answer.length.toLocaleString()} / 20,000
                </p>
                <button
                  type="button"
                  onClick={() => void submitAnswer()}
                  disabled={submitting || !answer.trim()}
                  className="btn-primary inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl text-sm disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2 dark:focus-visible:ring-offset-slate-900"
                >
                  {submitting ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <Send className="w-4 h-4" />
                  )}
                  {submitting ? 'Agent 正在决定下一步' : '提交回答'}
                </button>
              </div>
            </div>
          )}
        </section>

        <aside className="space-y-4 xl:sticky xl:top-8">
          <RuntimeRail session={session} />

          <div className="rounded-2xl border border-slate-200 dark:border-slate-700 bg-white/85 dark:bg-slate-800/80 p-5">
            <div className="flex items-center gap-2 mb-4">
              <ShieldCheck className="w-4 h-4 text-primary-500" />
              <h3 className="text-sm font-bold text-slate-900 dark:text-white">运行边界</h3>
            </div>
            <div className="grid grid-cols-3 gap-2 text-center">
              <BudgetCell value="3" label="模型步骤" />
              <BudgetCell value="1" label="工具调用" />
              <BudgetCell value="30s" label="单轮时限" />
            </div>
            <p className="mt-4 text-xs leading-5 text-slate-500 dark:text-slate-400">
              不使用 Memory、评分或报告。每次模型调用前都从数据库快照重建上下文。
            </p>
          </div>

          <div className="rounded-2xl border border-slate-200 dark:border-slate-700 bg-slate-950 text-slate-200 p-5 shadow-lg shadow-slate-950/10">
            <div className="flex items-center justify-between gap-3 mb-3">
              <div className="flex items-center gap-2">
                <SquareTerminal className="w-4 h-4 text-cyan-400" />
                <span className="font-mono text-xs text-slate-400">skill.snapshot</span>
              </div>
              {session.selectedSkillHash && (
                <button
                  type="button"
                  onClick={() => void copyHash()}
                  className="p-1.5 rounded-md text-slate-400 hover:text-white hover:bg-white/10 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400"
                  aria-label="复制 Skill hash"
                >
                  {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                </button>
              )}
            </div>
            <p className="font-mono text-sm font-semibold text-white break-all">
              {session.selectedSkillId || 'waiting_for_load_skill'}
            </p>
            <p className="font-mono text-[11px] leading-5 text-slate-500 break-all mt-2">
              {session.selectedSkillHash || 'hash pending'}
            </p>
          </div>
        </aside>
      </div>
    </div>
  );
}

interface SetupViewProps {
  jd: string;
  resume: string;
  creating: boolean;
  error: string;
  onJdChange: (value: string) => void;
  onResumeChange: (value: string) => void;
  onFillExample: () => void;
  onCreate: () => void;
  onRetrySession?: () => void;
}

function SetupView({
  jd,
  resume,
  creating,
  error,
  onJdChange,
  onResumeChange,
  onFillExample,
  onCreate,
  onRetrySession,
}: SetupViewProps) {
  return (
    <div className="max-w-6xl mx-auto pb-12">
      <header className="relative overflow-hidden rounded-3xl border border-primary-100 dark:border-primary-900/50 bg-white/85 dark:bg-slate-800/85 px-6 py-8 sm:px-9 sm:py-10 mb-6 shadow-xl shadow-primary-100/30 dark:shadow-slate-950/20">
        <div className="absolute -right-16 -top-24 w-64 h-64 rounded-full bg-gradient-to-br from-primary-200/50 to-cyan-200/40 dark:from-primary-800/20 dark:to-cyan-800/10 blur-2xl pointer-events-none" />
        <div className="relative max-w-3xl">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-primary-50 dark:bg-primary-900/30 text-primary-600 dark:text-primary-300 text-xs font-semibold tracking-wide mb-5">
            <Sparkles className="w-3.5 h-3.5" />
            AGENT LOOP · MVP V1
          </div>
          <h1 className="text-3xl sm:text-4xl font-bold text-slate-950 dark:text-white leading-tight">
            给模型完整事实，<br className="hidden sm:block" />让它决定下一道问题。
          </h1>
          <p className="mt-4 max-w-2xl text-sm sm:text-base leading-7 text-slate-600 dark:text-slate-300">
            输入职位描述与简历。模型先从 Skill 目录中自主选择方向，加载完整内容后开始一场最多 6 轮的自适应面试。
          </p>
        </div>
      </header>

      {error && (
        <ErrorBanner
          message={error}
          actionLabel={onRetrySession ? '重试加载' : undefined}
          onAction={onRetrySession}
        />
      )}

      <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_280px] gap-6 items-start">
        <section className="rounded-3xl border border-slate-200 dark:border-slate-700 bg-white/90 dark:bg-slate-800/85 shadow-xl shadow-slate-200/35 dark:shadow-slate-950/20 overflow-hidden">
          <div className="px-6 sm:px-8 py-5 border-b border-slate-100 dark:border-slate-700 flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary-500">Explicit context</p>
              <h2 className="text-lg font-bold text-slate-900 dark:text-white mt-1">准备面试上下文</h2>
            </div>
            <button
              type="button"
              onClick={onFillExample}
              className="inline-flex items-center gap-2 px-3 py-2 rounded-xl text-xs font-semibold text-primary-600 dark:text-primary-300 bg-primary-50 dark:bg-primary-900/25 hover:bg-primary-100 dark:hover:bg-primary-900/40 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            >
              <WandSparkles className="w-3.5 h-3.5" />
              填入联调示例
            </button>
          </div>

          <div className="p-6 sm:p-8 grid grid-cols-1 xl:grid-cols-2 gap-6">
            <ContextEditor
              id="agent-jd"
              label="职位描述"
              eyebrow="JD"
              icon={Target}
              value={jd}
              maxLength={20_000}
              placeholder="岗位职责、技术栈、业务场景和候选人级别……"
              onChange={onJdChange}
            />
            <ContextEditor
              id="agent-resume"
              label="候选人简历"
              eyebrow="RESUME"
              icon={FileText}
              value={resume}
              maxLength={100_000}
              placeholder="项目经历、技术能力、职责范围和代表性成果……"
              onChange={onResumeChange}
            />
          </div>

          <div className="px-6 sm:px-8 py-5 bg-slate-50/80 dark:bg-slate-900/35 border-t border-slate-100 dark:border-slate-700 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div className="flex items-start gap-2.5 text-xs leading-5 text-slate-500 dark:text-slate-400">
              <ShieldCheck className="w-4 h-4 mt-0.5 text-emerald-500 flex-shrink-0" />
              <span>原始文本仅作为显式 Context；页面不会发送 Skill id、评分参数或 Memory 标识。</span>
            </div>
            <button
              type="button"
              onClick={onCreate}
              disabled={creating || !jd.trim() || !resume.trim()}
              className="btn-primary inline-flex items-center justify-center gap-2 px-6 py-3 rounded-xl text-sm whitespace-nowrap disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2 dark:focus-visible:ring-offset-slate-900"
            >
              {creating ? <Loader2 className="w-4 h-4 animate-spin" /> : <Play className="w-4 h-4" />}
              {creating ? '模型正在选择 Skill' : '启动 Agent 面试'}
            </button>
          </div>
        </section>

        <aside className="space-y-4">
          <div className="rounded-2xl border border-slate-200 dark:border-slate-700 bg-white/85 dark:bg-slate-800/80 p-5">
            <div className="flex items-center gap-2 mb-4">
              <Bot className="w-4 h-4 text-primary-500" />
              <h3 className="text-sm font-bold text-slate-900 dark:text-white">模型将执行</h3>
            </div>
            <ol className="space-y-4">
              <ProtocolStep number="01" title="读取 Skill 描述" description="只看到 id、名称和简介" />
              <ProtocolStep number="02" title="调用 load_skill" description="自主选择一个面试方向" />
              <ProtocolStep number="03" title="生成第一题" description="加载完整 Skill 后再提问" />
            </ol>
          </div>

          <div className="rounded-2xl border border-amber-200/80 dark:border-amber-800/60 bg-amber-50/80 dark:bg-amber-900/15 p-5">
            <div className="flex items-center gap-2 text-amber-800 dark:text-amber-300 mb-2">
              <SquareTerminal className="w-4 h-4" />
              <h3 className="text-sm font-bold">联调前确认</h3>
            </div>
            <p className="text-xs leading-5 text-amber-700 dark:text-amber-400">
              Agent Loop 已默认启用；开始面试前，请先在“模型配置”中保存你自己的模型。
            </p>
          </div>
        </aside>
      </div>
    </div>
  );
}

interface ContextEditorProps {
  id: string;
  label: string;
  eyebrow: string;
  icon: typeof Target;
  value: string;
  maxLength: number;
  placeholder: string;
  onChange: (value: string) => void;
}

function ContextEditor({
  id,
  label,
  eyebrow,
  icon: Icon,
  value,
  maxLength,
  placeholder,
  onChange,
}: ContextEditorProps) {
  return (
    <div>
      <label htmlFor={id} className="flex items-center justify-between gap-3 mb-3">
        <span className="flex items-center gap-2 text-sm font-semibold text-slate-800 dark:text-slate-100">
          <Icon className="w-4 h-4 text-primary-500" />
          {label}
        </span>
        <span className="font-mono text-[10px] tracking-[0.16em] text-slate-400">{eyebrow}</span>
      </label>
      <textarea
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        maxLength={maxLength}
        rows={13}
        placeholder={placeholder}
        className="dark-input w-full rounded-2xl px-4 py-3.5 text-sm leading-6 resize-y outline-none placeholder:text-slate-400"
      />
      <p className="mt-2 text-right font-mono text-[10px] text-slate-400">
        {value.length.toLocaleString()} / {maxLength.toLocaleString()}
      </p>
    </div>
  );
}

function ProtocolStep({ number, title, description }: { number: string; title: string; description: string }) {
  return (
    <li className="flex gap-3">
      <span className="font-mono text-[10px] text-primary-500 mt-0.5">{number}</span>
      <div>
        <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">{title}</p>
        <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">{description}</p>
      </div>
    </li>
  );
}

function TurnConversation({
  turn,
  active,
  reduceMotion,
}: {
  turn: AgentInterviewTurn;
  active: boolean;
  reduceMotion: boolean;
}) {
  return (
    <motion.article
      initial={reduceMotion ? false : { opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25 }}
      className="space-y-3"
    >
      <div className="flex gap-3 sm:gap-4">
        <div className={`w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0 ${active ? 'bg-gradient-to-br from-primary-500 to-cyan-500 text-white shadow-md shadow-primary-500/20' : 'bg-primary-50 dark:bg-primary-900/25 text-primary-500 dark:text-primary-300'}`}>
          <Bot className="w-4 h-4" />
        </div>
        <div className={`flex-1 min-w-0 rounded-2xl rounded-tl-md px-4 py-3.5 border ${active ? 'bg-primary-50/70 dark:bg-primary-900/15 border-primary-200 dark:border-primary-800/50' : 'bg-slate-50 dark:bg-slate-900/35 border-slate-100 dark:border-slate-700/70'}`}>
          <div className="flex items-center justify-between gap-3 mb-1.5">
            <span className="text-[11px] font-semibold uppercase tracking-[0.14em] text-primary-500">
              Question {String(turn.turnNumber).padStart(2, '0')}
            </span>
            {active && <span className="text-[10px] text-cyan-600 dark:text-cyan-300 font-semibold">当前问题</span>}
          </div>
          <p className="text-sm sm:text-base leading-7 font-medium text-slate-900 dark:text-slate-100">
            {turn.question}
          </p>
        </div>
      </div>

      {turn.answer !== null && (
        <div className="flex gap-3 sm:gap-4 pl-7 sm:pl-12">
          <div className="flex-1 rounded-2xl rounded-tr-md px-4 py-3.5 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700">
            <p className="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400 mb-1.5">Your answer</p>
            <p className="text-sm leading-6 text-slate-700 dark:text-slate-300 whitespace-pre-wrap">
              {turn.answer}
            </p>
          </div>
        </div>
      )}
    </motion.article>
  );
}

function RuntimeRail({ session }: { session: AgentInterviewSession }) {
  return (
    <div className="rounded-2xl border border-slate-200 dark:border-slate-700 bg-white/85 dark:bg-slate-800/80 p-5">
      <div className="flex items-center justify-between gap-3 mb-5">
        <div className="flex items-center gap-2">
          <Target className="w-4 h-4 text-primary-500" />
          <h3 className="text-sm font-bold text-slate-900 dark:text-white">运行轨迹</h3>
        </div>
        <span className="font-mono text-[10px] text-slate-400">{session.currentTurn}/{session.maxTurns}</span>
      </div>

      <div className="relative">
        <div className="absolute left-[11px] top-3 bottom-3 w-px bg-slate-200 dark:bg-slate-700" />
        <ol className="relative space-y-4">
          {Array.from({ length: session.maxTurns }, (_, index) => index + 1).map((turnNumber) => {
            const turn = session.turns.find(item => item.turnNumber === turnNumber);
            const answered = turn?.answer !== null && turn?.answer !== undefined;
            const active = session.status === 'IN_PROGRESS' && session.currentTurn === turnNumber;
            return (
              <li key={turnNumber} className="flex items-center gap-3 min-h-6">
                <span className={`relative z-10 w-6 h-6 rounded-full border flex items-center justify-center flex-shrink-0 ${answered ? 'bg-emerald-500 border-emerald-500 text-white' : active ? 'bg-white dark:bg-slate-800 border-cyan-500 text-cyan-500 ring-4 ring-cyan-500/10' : 'bg-white dark:bg-slate-800 border-slate-300 dark:border-slate-600 text-slate-300 dark:text-slate-600'}`}>
                  {answered ? <Check className="w-3.5 h-3.5" /> : active ? <Circle className="w-2.5 h-2.5 fill-current" /> : <span className="font-mono text-[9px]">{turnNumber}</span>}
                </span>
                <div className="min-w-0 flex-1">
                  <p className={`text-xs font-semibold ${active ? 'text-cyan-700 dark:text-cyan-300' : answered ? 'text-slate-700 dark:text-slate-200' : 'text-slate-400 dark:text-slate-500'}`}>
                    第 {turnNumber} 轮
                  </p>
                  <p className="text-[10px] text-slate-400 truncate mt-0.5">
                    {answered ? '回答已写入快照' : active ? '等待你的回答' : '等待 Agent 推进'}
                  </p>
                </div>
              </li>
            );
          })}
        </ol>
      </div>
    </div>
  );
}

function BudgetCell({ value, label }: { value: string; label: string }) {
  return (
    <div className="rounded-xl bg-slate-50 dark:bg-slate-900/40 px-2 py-3">
      <p className="font-mono text-sm font-bold text-slate-800 dark:text-slate-100">{value}</p>
      <p className="text-[10px] text-slate-400 mt-1">{label}</p>
    </div>
  );
}

function CompletionPanel({ reason, onRestart }: { reason: string; onRestart: () => void }) {
  return (
    <div className="rounded-2xl border border-emerald-200 dark:border-emerald-800/60 bg-emerald-50/70 dark:bg-emerald-900/15 p-5 sm:p-6 text-center">
      <CheckCircle2 className="w-9 h-9 text-emerald-500 mx-auto" />
      <h3 className="text-lg font-bold text-emerald-900 dark:text-emerald-200 mt-3">六轮面试已完成</h3>
      <p className="text-sm text-emerald-700 dark:text-emerald-300 mt-1">{reason}</p>
      <p className="text-xs text-emerald-600/80 dark:text-emerald-400/80 mt-3">这个 MVP 不生成评分或报告。</p>
      <button
        type="button"
        onClick={onRestart}
        className="mt-5 inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-emerald-600 text-white text-sm font-semibold hover:bg-emerald-700 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:ring-offset-2"
      >
        <RotateCcw className="w-4 h-4" />
        开始新面试
      </button>
    </div>
  );
}

function FailurePanel({ reason, onRestart }: { reason: string; onRestart: () => void }) {
  return (
    <div className="rounded-2xl border border-red-200 dark:border-red-800/60 bg-red-50/70 dark:bg-red-900/15 p-5 sm:p-6">
      <div className="flex items-start gap-3">
        <AlertTriangle className="w-5 h-5 text-red-500 mt-0.5 flex-shrink-0" />
        <div>
          <h3 className="text-sm font-bold text-red-900 dark:text-red-200">Agent 运行已终止</h3>
          <p className="text-sm text-red-700 dark:text-red-300 mt-1">{reason}</p>
          <button
            type="button"
            onClick={onRestart}
            className="mt-4 inline-flex items-center gap-2 text-sm font-semibold text-red-700 dark:text-red-300 hover:text-red-900 dark:hover:text-red-100 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-red-500 rounded-lg"
          >
            <RotateCcw className="w-4 h-4" />
            返回并重试
          </button>
        </div>
      </div>
    </div>
  );
}

function ErrorBanner({
  message,
  actionLabel,
  onAction,
}: {
  message: string;
  actionLabel?: string;
  onAction?: () => void;
}) {
  return (
    <div className="mb-5 flex items-start gap-3 rounded-2xl border border-red-200 dark:border-red-800/60 bg-red-50/90 dark:bg-red-900/20 px-4 py-3.5 text-red-800 dark:text-red-200" role="alert">
      <AlertTriangle className="w-4 h-4 mt-0.5 flex-shrink-0" />
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold">请求没有完成</p>
        <p className="text-xs leading-5 text-red-700 dark:text-red-300 mt-0.5 break-words">{message}</p>
      </div>
      {actionLabel && onAction && (
        <button
          type="button"
          onClick={onAction}
          className="text-xs font-semibold whitespace-nowrap hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-red-500 rounded"
        >
          {actionLabel}
        </button>
      )}
    </div>
  );
}

function PageLoading() {
  return (
    <div className="min-h-[55vh] flex items-center justify-center">
      <div className="text-center">
        <div className="relative w-12 h-12 mx-auto">
          <div className="absolute inset-0 rounded-full border-2 border-primary-100 dark:border-primary-900" />
          <div className="absolute inset-0 rounded-full border-2 border-transparent border-t-primary-500 animate-spin" />
          <Bot className="absolute inset-0 m-auto w-5 h-5 text-primary-500" />
        </div>
        <p className="mt-4 text-sm font-medium text-slate-600 dark:text-slate-300">正在读取会话快照</p>
        <p className="mt-1 text-xs text-slate-400 flex items-center justify-center gap-1.5">
          <Clock3 className="w-3 h-3" />
          不使用客户端 Memory
        </p>
      </div>
    </div>
  );
}
