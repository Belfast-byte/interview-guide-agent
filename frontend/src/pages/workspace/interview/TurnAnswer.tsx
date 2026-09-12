import { Check, Loader2, RefreshCw, Send } from 'lucide-react';
import type { AdaptiveInterviewSession, AdaptiveInterviewTurn } from '../../../types/adaptiveInterview';
import CodeRepairPanel, { OriginalTaskCode } from '../../../components/codeRepair/CodeRepairPanel';
import CodeReviewFeedback from '../../../components/codeRepair/CodeReviewFeedback';
import type { AnswerDraft } from './useAnswerDraft';
import { hasAnswer } from './useAnswerDraft';

export function AnswerInput(props: {
  draft: AnswerDraft; update: (patch: Partial<AnswerDraft>) => void; disabled: boolean;
  codeRepair: boolean; onSubmit: () => void;
}) {
  const { draft, update, disabled, codeRepair, onSubmit } = props;
  return <div className="space-y-3 border-t border-line pt-5">
    <label htmlFor="workspace-answer" className="wk-label">{codeRepair ? '修改说明（可选）' : '你的回答'}</label>
    <textarea id="workspace-answer" value={draft.answer} onChange={event => update({ answer: event.target.value })}
      onKeyDown={event => { if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') { event.preventDefault(); onSubmit(); } }}
      rows={codeRepair ? 4 : 8} disabled={disabled} className="wk-input resize-y leading-7"
      placeholder={codeRepair ? '说明修复思路、边界条件和取舍，可不填写。' : '说明判断依据、实施方式、边界条件和取舍。'} />
    <div className="flex items-center justify-between gap-4"><p className="font-monosc text-[11px] text-wk-muted">Ctrl / ⌘ + Enter 提交</p>
      <button type="button" onClick={onSubmit} disabled={disabled || !(codeRepair ? draft.code : draft.answer).trim()}
        className="wk-cta px-5 py-2.5 text-sm"><Send className="h-4 w-4" />{codeRepair ? '提交修改' : '提交回答'}</button></div>
  </div>;
}

export function AcceptedAnswer(props: { turn: AdaptiveInterviewTurn; session: AdaptiveInterviewSession; disabled: boolean; retry: () => void }) {
  const { turn, session, disabled, retry } = props;
  if (!hasAnswer(turn)) return null;
  const canRetry = session.status === 'IN_PROGRESS' && turn.turnIndex === session.currentTurn && turn.answerStatus === 'RETRYABLE';
  return <div className="space-y-4">
    {turn.answer != null && <div className="border-l-2 border-line bg-raised px-5 py-4">
      <p className="wk-label mb-2">{turn.questionType === 'CODE_REPAIR' ? '修改说明' : '你的回答'}</p>
      <p className="whitespace-pre-wrap text-sm leading-7 text-ink-soft">{turn.answer}</p></div>}
    <CodeReviewFeedback review={turn.codeReview} feedback={turn.assessmentFeedback} code={turn.submittedCode} answer={turn.answer} />
    {canRetry ? <div className="space-y-3"><p className="text-sm text-wk-muted">{turn.answerError || '回答尚未处理完成，可以重试原答案。'}</p>
      <button type="button" disabled={disabled} onClick={retry} className="wk-cta px-5 py-2.5 text-sm"><RefreshCw className="h-4 w-4" />重试原答案</button></div>
      : <button type="button" disabled className="wk-cta px-5 py-2.5 text-sm">
        {turn.answerStatus === 'PROCESSING' ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
        {turn.answerStatus === 'PROCESSING' ? '回答处理中' : '已提交'}</button>}
  </div>;
}

export function TurnCode(props: {
  turn: AdaptiveInterviewTurn; session: AdaptiveInterviewSession; editable: boolean;
  draft: AnswerDraft; update: (patch: Partial<AnswerDraft>) => void; disabled: boolean;
}) {
  const { turn, session, editable, draft, update, disabled } = props;
  if (!turn.codeTask) return null;
  const prior = session.turns.filter(item => item.turnIndex < turn.turnIndex
    && item.codeTaskTurnIndex === turn.codeTaskTurnIndex && item.submittedCode != null).slice(-1)[0];
  const editingCode = editable && turn.questionType === 'CODE_REPAIR';
  if (!editingCode && turn.submittedCode == null && prior?.submittedCode == null) {
    return <OriginalTaskCode code={turn.codeTask.initialCode} />;
  }
  const code = editingCode ? draft.code : turn.submittedCode ?? prior!.submittedCode!;
  const currentLabel = editingCode ? '当前修改' : turn.submittedCode != null
    ? '本轮提交代码' : `第 ${prior!.turnIndex} 轮提交代码`;
  return <div className="space-y-5">
    {editable && session.mode === 'PRACTICE' && prior?.codeReview && <details className="border-l-2 border-line pl-4">
      <summary className="cursor-pointer text-sm text-ink-soft">上次提交的审阅反馈 · 第 {prior.turnIndex} 轮</summary>
      <CodeReviewFeedback review={prior.codeReview} feedback={prior.assessmentFeedback} code={prior.submittedCode} answer={prior.answer} />
    </details>}
    <CodeRepairPanel key={turn.turnIndex} task={turn.codeTask} code={code} currentLabel={currentLabel}
      readOnly={!editable || disabled || turn.questionType !== 'CODE_REPAIR'} onChange={value => update({ code: value })} />
  </div>;
}
