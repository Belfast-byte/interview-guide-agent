import { useEffect, useState } from 'react';
import { AlertCircle, ArrowLeft, ArrowRight, Loader2, RefreshCw } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { ROUTES } from '../../constants/routes';
import type { AdaptiveInterviewSession, AdaptiveInterviewTurn } from '../../types/adaptiveInterview';
import { CodeTaskBrief } from '../../components/codeRepair/CodeRepairPanel';
import { useAnswerSubmission, useSessionSnapshot } from './interview/useInterviewSession';
import { hasAnswer, useAnswerDraft } from './interview/useAnswerDraft';
import { DimensionStrip, SessionDetails } from './interview/InterviewProgress';
import { AcceptedAnswer, AnswerInput, TurnCode } from './interview/TurnAnswer';

function useInterviewPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const { user } = useAuth();
  const snapshot = useSessionSnapshot(sessionId);
  const draft = useAnswerDraft({ owner: user!.candidateId, session: snapshot.session });
  const submission = useAnswerSubmission(snapshot, draft.clearAccepted);
  const [viewIndex, setViewIndex] = useState<number | null>(null);
  useEffect(() => setViewIndex(null), [sessionId, submission.working]);
  const submit = () => {
    const session = snapshot.session;
    if (!session) return;
    const current = session.turns.find(turn => turn.turnIndex === session.currentTurn);
    const codeRepair = current?.questionType === 'CODE_REPAIR';
    if (!(codeRepair ? draft.draft.code : draft.draft.answer).trim()) return;
    void submission.submit({ turnIndex: session.currentTurn, answer: draft.draft.answer.trim() ? draft.draft.answer : null,
      ...(codeRepair ? { codeRepair: { code: draft.draft.code } } : {}) });
  };
  return { snapshot, draft, submission, viewIndex, setViewIndex, submit };
}
type PageState = ReturnType<typeof useInterviewPage>;

export default function InterviewSessionPage() {
  const page = useInterviewPage();
  const { snapshot } = page;
  if (!snapshot.session) return <div className="pt-16">
    {snapshot.loading ? <p className="flex items-center gap-3 text-sm"><Loader2 className="h-4 w-4 animate-spin" />正在读取面试快照…</p>
      : <><ErrorNotice message={snapshot.error || '会话不存在或已被删除。'} />
        <Link to={ROUTES.workspaceHistory} className="wk-btn-ghost mt-4">返回面试记录</Link></>}
  </div>;
  const session = snapshot.session;
  return <div className="pb-24">
    <SessionHeader page={page} session={session} />
    <ErrorNotice message={snapshot.error} />
    <ErrorNotice message={page.draft.error} />
    {session.status === 'FAILED' ? <ErrorNotice message={`面试创建失败：${session.failureReason ?? '未知原因'}`} />
      : <DimensionStrip dimensions={session.dimensions} />}
    <TurnPage page={page} session={session} />
    <div className="mt-10"><SessionDetails session={session} /></div>
  </div>;
}

function ErrorNotice({ message }: { message: string }) {
  return message ? <div role="alert" className="wk-error mt-6"><AlertCircle className="mt-0.5 h-4 w-4 flex-none" /><span>{message}</span></div> : null;
}

function SessionHeader({ page, session }: { page: PageState; session: AdaptiveInterviewSession }) {
  const label = session.status === 'CREATED' ? '正在准备' : session.status === 'FAILED' ? '创建失败'
    : session.status === 'COMPLETED' ? '已完成' : `第 ${session.currentTurn} / ${session.maxTurns} 轮`;
  return <header className="wk-rise flex flex-col gap-5 pt-10 lg:flex-row lg:items-end lg:justify-between">
    <div><Link to={ROUTES.workspaceHistory} className="mb-5 inline-flex items-center gap-2 text-[13px] text-wk-muted">
      <ArrowLeft className="h-3.5 w-3.5" />面试记录</Link>
      <p className="font-monosc text-[11px] tracking-[0.16em] text-cinnabar">{session.runtimeVersion}
        {session.llmProviderName && <span className="ml-3 text-wk-muted">{session.llmProviderName} · {session.llmModel}</span>}</p>
      <h1 className="mt-3 font-serifsc text-[28px] font-black tracking-wide text-ink sm:text-[34px]">自适应技术面试</h1></div>
    <div className="flex items-center gap-3"><span className="wk-tag">{label}</span>
      <button type="button" onClick={() => void page.snapshot.load()} disabled={page.snapshot.loading || page.submission.working} className="wk-btn-ghost">
        <RefreshCw className={`h-3.5 w-3.5 ${page.snapshot.loading ? 'animate-spin' : ''}`} />刷新</button></div>
  </header>;
}

function TurnPage({ page, session }: { page: PageState; session: AdaptiveInterviewSession }) {
  const turns = session.turns;
  const current = turns.find(turn => turn.turnIndex === session.currentTurn);
  const answerable = session.status === 'IN_PROGRESS' && current && !hasAnswer(current);
  const last = turns.slice(-1)[0]?.turnIndex ?? 1;
  const live = page.submission.working ? last + 1 : answerable ? session.currentTurn : last;
  const index = page.viewIndex ?? live;
  const turn = turns.find(item => item.turnIndex === index);
  const editable = page.viewIndex === null && !page.submission.working && Boolean(answerable);
  return <section className="mt-8">
    {turns.length > 0 && <div className="flex items-center justify-between border-y border-line py-2.5">
      <button type="button" disabled={index <= turns[0].turnIndex} onClick={() => page.setViewIndex(index - 1)} className="wk-btn-ghost">
        <ArrowLeft className="h-3.5 w-3.5" />上一题</button>
      <p className="font-monosc text-xs text-wk-muted">Q{String(index).padStart(2, '0')} / {session.maxTurns}{editable && ' · 待回答'}</p>
      <button type="button" disabled={page.viewIndex === null} onClick={() => page.setViewIndex(index + 1 >= live ? null : index + 1)} className="wk-btn-ghost">
        下一题<ArrowRight className="h-3.5 w-3.5" /></button></div>}
    <div className="mt-8">{turn ? <TurnContent page={page} session={session} turn={turn} editable={editable} />
      : <p className="whitespace-pre-wrap text-sm leading-8 text-ink-soft">{page.submission.streamingQuestion || (session.status === 'CREATED'
        ? '正在根据职位描述与简历规划面试维度并生成首题，完成后自动进入面试。'
        : page.submission.stage === 'assessing' ? '正在评估你的回答…' : '正在生成下一题…')}</p>}</div>
  </section>;
}

function TurnContent(props: { page: PageState; session: AdaptiveInterviewSession; turn: AdaptiveInterviewTurn; editable: boolean }) {
  const { page, session, turn, editable } = props;
  const disabled = page.submission.working || page.snapshot.recoveryPending;
  return <article className={`wk-rise grid min-w-0 items-start gap-8 ${turn.codeTask ? 'lg:grid-cols-[4fr_6fr]' : 'max-w-3xl'}`}>
    <div className="space-y-6"><div className="border-l-2 border-ink pl-5">
      <p className="mb-2 font-monosc text-xs text-cinnabar">Q{String(turn.turnIndex).padStart(2, '0')}</p>
      <p className="whitespace-pre-wrap text-[15px] font-medium leading-8">{turn.question}</p></div>
      {turn.codeTask && <CodeTaskBrief task={turn.codeTask} />}</div>
    <div className="min-w-0 space-y-5">
      {turn.questionType === 'CODE_REPAIR' && !turn.codeTask && <ErrorNotice message="代码任务缺失，无法展示本题。请刷新读取正式题目。" />}
      <TurnCode turn={turn} session={session} editable={editable} draft={page.draft.draft} update={page.draft.update} disabled={disabled} />
      {editable ? <AnswerInput draft={page.draft.draft} update={page.draft.update}
        disabled={disabled || (turn.questionType === 'CODE_REPAIR' && !turn.codeTask)} codeRepair={turn.questionType === 'CODE_REPAIR'} onSubmit={page.submit} />
        : <AcceptedAnswer key={turn.turnIndex} turn={turn} session={session} disabled={disabled} retry={() => void page.submission.submit(null)} />}
    </div>
  </article>;
}
