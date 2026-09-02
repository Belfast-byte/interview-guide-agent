import type { RoadmapPhase } from '../auditTypes';
import { SectionHeading, SEVERITY_STYLES } from './ui';
import type { AuditSeverity } from '../auditTypes';

export default function Roadmap({ phases }: { phases: readonly RoadmapPhase[] }) {
  return (
    <section id="roadmap" className="scroll-mt-8">
      <SectionHeading
        eyebrow="Roadmap"
        title="分阶段重构路线图"
        description="顺序即原则：P0 先修正确性缺口，P1 再删状态协议，P2 才恢复 Agent 自主权，P3 清理形状，P4 只在有测量证据后优化。性能机制永远不进入 correctness 模型。"
      />
      <ol className="space-y-3">
        {phases.map((phase, i) => (
          <li key={phase.id} className="relative grid gap-3 sm:grid-cols-[3.5rem_1fr]">
            {i < phases.length - 1 && (
              <span className="absolute left-[21px] top-12 bottom-[-12px] hidden w-px bg-zinc-800 sm:block" aria-hidden />
            )}
            <div>
              <span
                className={`inline-flex h-11 w-11 items-center justify-center rounded-lg border font-mono text-[13px] font-semibold ${
                  SEVERITY_STYLES[(phase.id as AuditSeverity)] ?? SEVERITY_STYLES.P3
                }`}
              >
                {phase.id}
              </span>
            </div>
            <div className="rounded-lg border border-zinc-800 bg-zinc-900/30 p-4">
              <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                <h3 className="text-[14px] font-semibold text-zinc-100">{phase.title}</h3>
                <p className="text-[12px] text-zinc-500">{phase.objective}</p>
              </div>
              <ul className="mt-3 grid gap-1.5 sm:grid-cols-2">
                {phase.changes.map(change => (
                  <li key={change} className="flex gap-2 text-[12.5px] leading-snug text-zinc-300">
                    <span className="mt-[6px] h-1 w-1 shrink-0 rounded-full bg-zinc-500" aria-hidden />
                    {change}
                  </li>
                ))}
              </ul>
              <details className="mt-3 border-t border-zinc-800/70 pt-2.5">
                <summary className="cursor-pointer font-mono text-[11px] text-zinc-500 transition-colors hover:text-zinc-300">
                  必须通过的验证（{phase.tests.length}）
                </summary>
                <ul className="mt-2 space-y-1">
                  {phase.tests.map(test => (
                    <li key={test} className="flex gap-2 text-[12px] text-zinc-400">
                      <span className="mt-[6px] h-1 w-1 shrink-0 rounded-full border border-zinc-600" aria-hidden />
                      {test}
                    </li>
                  ))}
                </ul>
              </details>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
