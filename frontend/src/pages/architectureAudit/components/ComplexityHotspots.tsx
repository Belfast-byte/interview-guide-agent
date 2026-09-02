import type { Hotspot } from '../auditTypes';
import { DecisionBadge, KindBadge, SectionHeading } from './ui';

export default function ComplexityHotspots({ hotspots }: { hotspots: readonly Hotspot[] }) {
  const max = Math.max(...hotspots.map(h => h.lines));
  return (
    <section id="hotspots" className="scroll-mt-8">
      <SectionHeading
        eyebrow="Complexity Hotspots"
        title="复杂度热点"
        description="行数是审计范围口径（已经实测复核），不是删除承诺；范围存在交叉，不可相加。真正必须持久化执行状态的只有 SandboxExecution、AnalysisJob 等真实外部任务。"
      />
      <div className="overflow-hidden rounded-lg border border-zinc-800">
        {hotspots.map(hotspot => (
          <div
            key={hotspot.label}
            className="grid grid-cols-[1fr_auto] items-center gap-x-4 gap-y-1.5 border-b border-zinc-800/70 bg-zinc-900/30 px-4 py-3 last:border-b-0 sm:grid-cols-[minmax(0,18rem)_1fr_auto]"
          >
            <div className="min-w-0">
              <div className="truncate text-[13px] font-medium text-zinc-200">{hotspot.label}</div>
              <div className="mt-0.5 text-[11px] text-zinc-500">{hotspot.disposition}</div>
            </div>
            <div className="col-span-2 flex items-center gap-3 sm:col-span-1">
              <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-zinc-800">
                <div
                  className={`h-full rounded-full ${hotspot.kind === 'accidental' ? 'bg-rose-400/70' : 'bg-zinc-500'}`}
                  style={{ width: `${Math.round((hotspot.lines / max) * 100)}%` }}
                />
              </div>
              <span className="w-14 text-right font-mono text-[12px] text-zinc-300">
                {hotspot.lines.toLocaleString()}
              </span>
            </div>
            <div className="col-start-2 flex items-center gap-1.5 sm:col-start-auto">
              <KindBadge value={hotspot.kind} />
              <DecisionBadge value={hotspot.decision} />
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
