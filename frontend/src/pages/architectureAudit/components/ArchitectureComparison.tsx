import { useState } from 'react';
import { ArrowRight } from 'lucide-react';
import type { ArchNode, ArchitectureLane } from '../auditTypes';
import { SectionHeading } from './ui';

const TONE_STYLES: Record<ArchNode['tone'], string> = {
  fact: 'border-emerald-400/25 bg-emerald-400/5 text-emerald-100/90',
  risk: 'border-rose-400/30 bg-rose-400/5 text-rose-200/90',
  compute: 'border-zinc-700 bg-zinc-800/50 text-zinc-300',
  'side-effect': 'border-sky-400/25 bg-sky-400/5 text-sky-100/90',
  removed: 'border-zinc-800 bg-transparent text-zinc-600 line-through',
};

const MINIMAL_RESPONSIBILITIES = [
  'Session：生命周期、最大轮次、一个并发版本',
  'Turn：用户已看到的问题、回答和 provenance',
  'Planner：LLM 提案；Java 只校验 catalog、范围和硬上限',
  'ContextAssembler：从领域事实组装一次性上下文，自动加载固定 Skill',
  'AgentLoop：仅在有真实动态只读 Tool 时执行 0..N 次',
  'ToolExecutor：只读 Tool 不持久化；副作用 Tool 不走 generic executor',
  'SandboxExecution：沙箱唯一执行事实源',
  'Evidence 持久化；Coverage 默认按 Plan / Turn / Assessment 推导',
  'Report：保持当前确定性聚合方式',
];

function LaneView({ lane }: { lane: ArchitectureLane }) {
  return (
    <div className="grid gap-2 border-b border-zinc-800/70 px-4 py-3 last:border-b-0 sm:grid-cols-[9rem_1fr] sm:items-start">
      <div className="pt-1 font-mono text-[10px] uppercase tracking-[0.18em] text-zinc-500">{lane.label}</div>
      <div className="flex flex-wrap items-center gap-y-2">
        {lane.nodes.map((node, i) => (
          <span key={node.name} className="flex items-center">
            {i > 0 && <ArrowRight className="mx-1.5 h-3 w-3 shrink-0 text-zinc-700" aria-hidden />}
            <span className={`rounded border px-2 py-1 text-[12px] ${TONE_STYLES[node.tone]}`}>
              {node.name}
              {node.note && <span className="ml-1.5 text-[10px] opacity-70">{node.note}</span>}
            </span>
          </span>
        ))}
      </div>
    </div>
  );
}

export default function ArchitectureComparison({
  before,
  after,
}: {
  before: readonly ArchitectureLane[];
  after: readonly ArchitectureLane[];
}) {
  const [side, setSide] = useState<'before' | 'after'>('before');
  return (
    <section id="arch" className="scroll-mt-8">
      <SectionHeading
        eyebrow="Architecture"
        title="Before / After 架构对比"
        description="After 不引入新框架，只保留当前已有业务概念：领域事实 + 请求内组装 + 短事务 + 副作用边界。"
      />
      <div className="mb-3 inline-flex rounded-md border border-zinc-800 p-0.5" role="group" aria-label="架构版本">
        {(['before', 'after'] as const).map(value => (
          <button
            key={value}
            type="button"
            aria-pressed={side === value}
            onClick={() => setSide(value)}
            className={`rounded px-3 py-1 font-mono text-[11px] uppercase tracking-wide transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-zinc-400 ${
              side === value ? 'bg-zinc-800 text-zinc-100' : 'text-zinc-500 hover:text-zinc-300'
            }`}
          >
            {value}
          </button>
        ))}
      </div>

      <div className="rounded-lg border border-zinc-800 bg-zinc-900/30">
        {(side === 'before' ? before : after).map(lane => (
          <LaneView key={lane.label} lane={lane} />
        ))}
      </div>

      {side === 'after' && (
        <details className="mt-3 rounded-lg border border-zinc-800 bg-zinc-900/30 px-4 py-3">
          <summary className="cursor-pointer text-[12px] text-zinc-400 transition-colors hover:text-zinc-200">
            最小职责清单（9 条）
          </summary>
          <ul className="mt-3 space-y-1.5">
            {MINIMAL_RESPONSIBILITIES.map(item => (
              <li key={item} className="flex gap-2 text-[12.5px] text-zinc-300">
                <span className="mt-[7px] h-1 w-1 shrink-0 rounded-full bg-zinc-600" aria-hidden />
                {item}
              </li>
            ))}
          </ul>
        </details>
      )}

      <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 font-mono text-[10px] text-zinc-600">
        <span><span className="mr-1 inline-block h-2 w-2 rounded-sm border border-emerald-400/40 bg-emerald-400/10 align-middle" />领域事实</span>
        <span><span className="mr-1 inline-block h-2 w-2 rounded-sm border border-rose-400/40 bg-rose-400/10 align-middle" />可复制 / 可删除</span>
        <span><span className="mr-1 inline-block h-2 w-2 rounded-sm border border-zinc-600 bg-zinc-800/60 align-middle" />请求内计算</span>
        <span><span className="mr-1 inline-block h-2 w-2 rounded-sm border border-sky-400/40 bg-sky-400/10 align-middle" />外部副作用</span>
      </div>
    </section>
  );
}
