import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import type { AuditDecision, DecisionItem, RootCause } from '../auditTypes';
import { DecisionBadge, SectionHeading, SeverityBadge } from './ui';

const FILTERS: readonly (AuditDecision | 'ALL')[] = ['ALL', 'DELETE', 'SIMPLIFY', 'KEEP', 'REDESIGN'];

export default function DecisionLedger({
  decisions,
  causes,
}: {
  decisions: readonly DecisionItem[];
  causes: readonly RootCause[];
}) {
  const [filter, setFilter] = useState<AuditDecision | 'ALL'>('ALL');
  const [openId, setOpenId] = useState<string | null>(null);
  const causeIndex = new Map(causes.map(c => [c.id, c.index]));
  const visible = filter === 'ALL' ? decisions : decisions.filter(d => d.decision === filter);

  return (
    <section id="decisions" className="scroll-mt-8">
      <SectionHeading
        eyebrow="Decision Ledger"
        title="决策账本：Delete / Simplify / Keep / Redesign"
        description="KEEP 是业务必要复杂度，其余是偶然复杂度的处置方式。点击行展开理由、替代机制与风险。"
      />
      <div className="mb-3 flex flex-wrap gap-1.5" role="group" aria-label="按决策过滤">
        {FILTERS.map(value => {
          const count = value === 'ALL' ? decisions.length : decisions.filter(d => d.decision === value).length;
          const isActive = filter === value;
          return (
            <button
              key={value}
              type="button"
              aria-pressed={isActive}
              onClick={() => setFilter(value)}
              className={`rounded-md border px-2.5 py-1 font-mono text-[11px] tracking-wide transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-zinc-400 ${
                isActive
                  ? 'border-zinc-600 bg-zinc-800 text-zinc-100'
                  : 'border-zinc-800 text-zinc-500 hover:border-zinc-700 hover:text-zinc-300'
              }`}
            >
              {value === 'ALL' ? '全部' : value}
              <span className="ml-1.5 text-zinc-500">{count}</span>
            </button>
          );
        })}
      </div>

      <div className="overflow-hidden rounded-lg border border-zinc-800">
        {visible.map(item => {
          const isOpen = openId === item.id;
          return (
            <div
              key={item.id}
              className="audit-decision-row border-b border-zinc-800/70 bg-zinc-900/30 last:border-b-0"
              data-decision={item.decision}
            >
              <button
                type="button"
                aria-expanded={isOpen}
                onClick={() => setOpenId(isOpen ? null : item.id)}
                className="grid w-full grid-cols-[auto_1fr_auto] items-center gap-3 px-4 py-2.5 text-left transition-colors hover:bg-zinc-800/30 focus-visible:outline focus-visible:outline-2 focus-visible:outline-zinc-400"
              >
                <DecisionBadge value={item.decision} />
                <span className="min-w-0">
                  <span className="block truncate text-[13px] text-zinc-200">{item.title}</span>
                  <span className="mt-0.5 block truncate text-[11px] text-zinc-500">{item.reason}</span>
                </span>
                <span className="flex items-center gap-2">
                  <span className="hidden font-mono text-[10px] text-zinc-600 sm:inline">
                    {causeIndex.get(item.rootCauseId)} · {item.stage}
                  </span>
                  <ChevronDown
                    className={`h-3.5 w-3.5 text-zinc-500 transition-transform ${isOpen ? 'rotate-180' : ''}`}
                  />
                </span>
              </button>
              {isOpen && (
                <dl className="grid gap-3 border-t border-zinc-800/70 px-4 py-3 text-[12.5px] leading-relaxed sm:grid-cols-3">
                  <div>
                    <dt className="font-mono text-[10px] uppercase tracking-[0.18em] text-zinc-500">理由</dt>
                    <dd className="mt-1 text-zinc-300">{item.reason}</dd>
                  </div>
                  <div>
                    <dt className="font-mono text-[10px] uppercase tracking-[0.18em] text-zinc-500">替代 / 保留方式</dt>
                    <dd className="mt-1 text-zinc-300">{item.replacement}</dd>
                  </div>
                  <div>
                    <dt className="font-mono text-[10px] uppercase tracking-[0.18em] text-zinc-500">风险</dt>
                    <dd className="mt-1 text-zinc-400">{item.risk}</dd>
                  </div>
                </dl>
              )}
            </div>
          );
        })}
      </div>
      <div className="mt-2 flex items-center gap-2 text-[11px] text-zinc-600">
        <SeverityBadge value="P0" />
        <span>阶段标注对应下方路线图的实施阶段。</span>
      </div>
    </section>
  );
}
