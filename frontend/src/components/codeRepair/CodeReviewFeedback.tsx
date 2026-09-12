import { useState } from 'react';
import type { AssessmentFeedback, CodeRepairReview, SourceQuote } from '../../types/codeRepair';

const RESULTS = { SATISFIED: '满足要求', NOT_SATISFIED: '尚未满足', UNDETERMINED: '无法确定' };
export default function CodeReviewFeedback(props: {
  review?: CodeRepairReview | null; feedback?: AssessmentFeedback | null;
  code?: string | null; answer?: string | null;
}) {
  const [selected, setSelected] = useState<SourceQuote | null>(null);
  if (!props.review && !props.feedback) return null;
  const source = selected?.source === 'SUBMITTED_CODE' ? props.code : props.answer;
  return <section className="space-y-3 border-t border-line pt-4" aria-label="模型代码审阅反馈">
    <h3 className="wk-label">模型代码审阅</h3>
    {props.feedback && <p className="text-sm leading-7">{props.feedback.depthLevel} · {props.feedback.rationale}</p>}
    <ul className="space-y-3">{props.review?.checks.map(check => <li key={check.checkId} className="border-l-2 border-line pl-3">
      <p className="text-xs font-medium">{check.checkId} · {RESULTS[check.result]}</p>
      <p className="mt-1 text-sm leading-6 text-ink-soft">{check.reason}</p></li>)}</ul>
    {props.feedback?.evidenceQuotes.map((quote, index) => <button key={index} type="button"
      className="block w-full border-l-2 border-line bg-raised p-3 text-left text-sm" onClick={() => setSelected(quote)}>
      <span className="mb-1 block text-xs text-wk-muted">{quote.source === 'SUBMITTED_CODE' ? '提交代码' : '修改说明'} · 定位证据</span>
      <code className="whitespace-pre-wrap break-words">{quote.quote}</code></button>)}
    {selected && source != null && <EvidenceSource source={source} quote={selected} />}
  </section>;
}

export function EvidenceSource({ source, quote }: { source: string; quote: SourceQuote }) {
  const start = quote.startOffset;
  if (start == null) return <p className="text-xs text-wk-muted">历史证据未记录位置。</p>;
  const end = start + quote.quote.length;
  if (source.slice(start, end) !== quote.quote) {
    return <p role="alert" className="wk-error">证据位置与正式原文不一致，无法定位。</p>;
  }
  const line = source.slice(0, start).split('\n').length;
  return <div className="min-w-0"><p className="mb-2 text-xs text-wk-muted">正式原文 · 第 {line} 行</p>
    <pre className="max-h-80 overflow-auto border border-line p-3 font-monosc text-xs leading-6" aria-label="证据原文定位">
      {source.slice(0, start)}<mark className="bg-cinnabar-wash text-ink">{source.slice(start, end)}</mark>{source.slice(end)}
    </pre></div>;
}
