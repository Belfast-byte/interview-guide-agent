import { ArrowDown, ArrowRight } from 'lucide-react';
import type { CodeEvidence, RootCause } from '../auditTypes';
import { DecisionBadge, KindBadge, SectionHeading, SeverityBadge } from './ui';

interface Props {
  causes: readonly RootCause[];
  evidence: readonly CodeEvidence[];
  selectedId: string;
  onSelect: (id: string) => void;
  onViewEvidence: (rootCauseId: string) => void;
}

function ChainStep({
  label,
  children,
  last = false,
}: {
  label: string;
  children: React.ReactNode;
  last?: boolean;
}) {
  return (
    <li className="relative pl-8">
      {!last && <span className="absolute left-[7px] top-6 bottom-0 w-px bg-zinc-800" aria-hidden />}
      <span
        className={`absolute left-0 top-1.5 flex h-[15px] w-[15px] items-center justify-center rounded-full border ${
          last ? 'border-sky-400/50 bg-sky-400/15' : 'border-zinc-700 bg-zinc-900'
        }`}
        aria-hidden
      >
        {last ? (
          <ArrowRight className="h-2 w-2 text-sky-300" />
        ) : (
          <ArrowDown className="h-2 w-2 text-zinc-500" />
        )}
      </span>
      <div className="font-mono text-[10px] uppercase tracking-[0.2em] text-zinc-500">{label}</div>
      <div className="mt-1 pb-5 text-[13px] leading-relaxed text-zinc-300">{children}</div>
    </li>
  );
}

export default function RootCauseMap({ causes, evidence, selectedId, onSelect, onViewEvidence }: Props) {
  const selected = causes.find(c => c.id === selectedId) ?? causes[0];
  const linked = evidence.filter(e => e.rootCauseId === selected.id);
  const linkedRefs = linked.flatMap(e => e.refs).slice(0, 4);

  return (
    <section id="causes" className="scroll-mt-8">
      <SectionHeading
        eyebrow="Root Cause Map"
        title="核心问题地图：5 个根因"
        description="Top 10 问题合并为 5 个根因。每条链路按「根因 → 相关模块 / 代码 → 次生复杂度 → 实际影响 → 重构建议」展开；点击左侧根因切换，证据浏览器会同步过滤。"
      />
      <div className="grid gap-3 lg:grid-cols-[minmax(0,20rem)_1fr]">
        <div className="flex gap-2 overflow-x-auto lg:flex-col lg:overflow-visible" role="group" aria-label="根因列表">
          {causes.map(cause => {
            const isActive = cause.id === selected.id;
            return (
              <button
                key={cause.id}
                type="button"
                aria-pressed={isActive}
                onClick={() => {
                  onSelect(cause.id);
                  onViewEvidence(cause.id);
                }}
                className={`min-w-[220px] rounded-lg border p-3 text-left transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-zinc-400 lg:min-w-0 ${
                  isActive
                    ? 'border-zinc-600 bg-zinc-800/60'
                    : 'border-zinc-800 bg-zinc-900/30 hover:border-zinc-700'
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-mono text-[10px] tracking-wide text-zinc-500">{cause.index}</span>
                  <SeverityBadge value={cause.severity} />
                </div>
                <div className={`mt-1.5 text-[13px] font-medium leading-snug ${isActive ? 'text-zinc-50' : 'text-zinc-300'}`}>
                  {cause.title}
                </div>
                <div className="mt-2">
                  <DecisionBadge value={cause.decision} />
                </div>
              </button>
            );
          })}
        </div>

        <div className="audit-cause-detail rounded-lg border border-zinc-800 bg-zinc-900/30 p-5">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-[11px] text-zinc-500">{selected.index}</span>
            <SeverityBadge value={selected.severity} />
            <KindBadge value={selected.kind} />
            <DecisionBadge value={selected.decision} />
          </div>
          <h3 className="mt-2 text-base font-semibold text-zinc-50">{selected.title}</h3>

          <ol className="mt-4">
            <ChainStep label="根因">
              <p className="text-zinc-300">{selected.thesis}</p>
            </ChainStep>
            <ChainStep label="相关模块 / 代码">
              <div className="flex flex-wrap gap-1.5">
                {selected.modules.map(m => (
                  <span key={m} className="rounded border border-zinc-700/80 bg-zinc-800/50 px-2 py-0.5 font-mono text-[11px] text-zinc-300">
                    {m}
                  </span>
                ))}
              </div>
              <div className="mt-2 space-y-1">
                {linkedRefs.map(ref => (
                  <div key={ref} className="break-all font-mono text-[11px] text-zinc-500">
                    {ref.replace('app/src/main/java/interview/guide/modules/interview/agent/adaptive/', '…/')}
                  </div>
                ))}
                {linked.length > 0 && (
                  <div className="font-mono text-[10px] text-zinc-600">
                    共 {linked.length} 条证据 · {linked.flatMap(e => e.refs).length} 处引用
                  </div>
                )}
              </div>
            </ChainStep>
            <ChainStep label="次生复杂度">
              <ul className="space-y-1">
                {selected.secondaryComplexity.map(item => (
                  <li key={item} className="flex gap-2">
                    <span className="mt-[7px] h-1 w-1 shrink-0 rounded-full bg-rose-400/60" aria-hidden />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            </ChainStep>
            <ChainStep label="实际影响">
              <ul className="space-y-1">
                {selected.impacts.map(item => (
                  <li key={item} className="flex gap-2">
                    <span className="mt-[7px] h-1 w-1 shrink-0 rounded-full bg-amber-300/60" aria-hidden />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            </ChainStep>
            <ChainStep label="重构建议" last>
              <p className="rounded-md border border-sky-400/20 bg-sky-400/5 p-3 text-sky-100/90">
                {selected.recommendation}
              </p>
            </ChainStep>
          </ol>

          <a
            href="#evidence"
            onClick={() => onViewEvidence(selected.id)}
            className="inline-flex items-center gap-1.5 rounded-md border border-zinc-700 px-3 py-1.5 text-[12px] text-zinc-200 transition-colors hover:border-zinc-500 hover:text-zinc-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-zinc-400"
          >
            在证据浏览器中查看 {linked.length} 条代码证据
            <ArrowRight className="h-3.5 w-3.5" />
          </a>
        </div>
      </div>
    </section>
  );
}
