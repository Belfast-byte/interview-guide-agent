import type { AdaptiveEvidenceReference } from '../../../types/adaptiveInterview';
import { EvidenceSource } from '../../../components/codeRepair/CodeReviewFeedback';

export default function ReportEvidence({ evidence }: { evidence: AdaptiveEvidenceReference }) {
  const locator = evidence.quoteLocator;
  const code = locator?.source === 'SUBMITTED_CODE';
  const source = code ? evidence.submittedCode : evidence.answer;
  return <blockquote className="border-l-2 border-line bg-raised px-4 py-3">
    <p className="mb-1 font-monosc text-[10px] tracking-[0.12em] text-wk-muted">
      {evidence.type === 'QUOTE' ? `${code ? '提交代码' : '回答原文'} · Q${String(evidence.turnIndex).padStart(2, '0')}` : `工具结果 · ${evidence.toolResult?.toolName ?? ''}`}</p>
    <p className="whitespace-pre-wrap text-[13px] leading-6 text-ink-soft">{evidence.quote ?? evidence.toolResult?.output}</p>
    {locator && evidence.quote && source != null && <details className="mt-3"><summary className="cursor-pointer text-xs">定位正式原文</summary>
      <EvidenceSource source={source} quote={{ source: locator.source, quote: evidence.quote, startOffset: locator.startOffset }} /></details>}
  </blockquote>;
}
