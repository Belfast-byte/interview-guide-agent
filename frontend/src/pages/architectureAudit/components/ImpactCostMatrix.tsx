import { useState } from 'react';
import type { MatrixItem } from '../auditTypes';
import { DECISION_DOTS, SectionHeading, SeverityBadge } from './ui';

const W = 640;
const H = 400;
const PAD = 44;

/** SVG fill 不能走 Tailwind bg-*，显式给出与 DECISION_DOTS 一致的颜色 */
const DECISION_FILLS: Record<MatrixItem['decision'], string> = {
  DELETE: '#fb7185',
  SIMPLIFY: '#fcd34d',
  KEEP: '#34d399',
  REDESIGN: '#38bdf8',
};

const QUADRANTS = [
  { x: 0, y: 1, label: '高影响 · 低成本 → 立即做', anchor: 'start' as const },
  { x: 1, y: 1, label: '高影响 · 高成本 → 规划做', anchor: 'end' as const },
  { x: 0, y: 0, label: '低影响 · 低成本 → 顺手做', anchor: 'start' as const },
  { x: 1, y: 0, label: '低影响 · 高成本 → 暂不做', anchor: 'end' as const },
];

function toX(cost: number) {
  return PAD + (cost / 100) * (W - PAD * 2);
}
function toY(impact: number) {
  return H - PAD - (impact / 100) * (H - PAD * 2);
}

export default function ImpactCostMatrix({ items }: { items: readonly MatrixItem[] }) {
  const [activeId, setActiveId] = useState<string | null>(null);
  const sorted = [...items].sort((a, b) => b.impact - a.impact || a.cost - b.cost);

  return (
    <section id="matrix" className="scroll-mt-8">
      <SectionHeading
        eyebrow="Impact × Cost"
        title="影响 × 成本矩阵"
        description="影响取自审计严重度与受影响面，成本取自涉及代码量与依赖面。P0 全部落在「立即做」象限——先修正确性，再删复杂度。"
      />
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
        <div className="rounded-lg border border-zinc-800 bg-zinc-900/30 p-3">
          <svg viewBox={`0 0 ${W} ${H}`} className="h-auto w-full" role="img" aria-label="影响成本矩阵散点图">
            {/* 象限分隔 */}
            <line x1={toX(50)} y1={PAD} x2={toX(50)} y2={H - PAD} stroke="#3f3f46" strokeDasharray="4 4" strokeWidth="1" />
            <line x1={PAD} y1={toY(50)} x2={W - PAD} y2={toY(50)} stroke="#3f3f46" strokeDasharray="4 4" strokeWidth="1" />
            {/* 轴 */}
            <line x1={PAD} y1={H - PAD} x2={W - PAD} y2={H - PAD} stroke="#52525b" strokeWidth="1" />
            <line x1={PAD} y1={PAD} x2={PAD} y2={H - PAD} stroke="#52525b" strokeWidth="1" />
            <text x={W - PAD} y={H - PAD + 22} textAnchor="end" fill="#71717a" fontSize="11" fontFamily="monospace">
              成本 →
            </text>
            <text x={PAD - 10} y={PAD - 14} textAnchor="start" fill="#71717a" fontSize="11" fontFamily="monospace">
              影响 →
            </text>
            {QUADRANTS.map(q => (
              <text
                key={q.label}
                x={q.x === 0 ? PAD + 8 : W - PAD - 8}
                y={q.y === 1 ? PAD + 16 : H - PAD - 10}
                textAnchor={q.anchor}
                fill="#52525b"
                fontSize="10"
              >
                {q.label}
              </text>
            ))}
            {items.map(item => {
              const active = item.id === activeId;
              return (
                <g key={item.id} onMouseEnter={() => setActiveId(item.id)} onMouseLeave={() => setActiveId(null)}>
                  <circle cx={toX(item.cost)} cy={toY(item.impact)} r={active ? 14 : 10} fill="transparent" />
                  <circle
                    cx={toX(item.cost)}
                    cy={toY(item.impact)}
                    r={6}
                    fill={DECISION_FILLS[item.decision]}
                    opacity={active ? 1 : 0.75}
                    stroke={active ? '#fafafa' : 'none'}
                    strokeWidth={active ? 1.5 : 0}
                  />
                  <title>{`${item.label} · 影响 ${item.impact} / 成本 ${item.cost} · ${item.decision} · ${item.stage}`}</title>
                </g>
              );
            })}
          </svg>
        </div>

        <ol className="overflow-hidden rounded-lg border border-zinc-800">
          {sorted.map((item, i) => (
            <li
              key={item.id}
              onMouseEnter={() => setActiveId(item.id)}
              onMouseLeave={() => setActiveId(null)}
              className={`flex items-center gap-3 border-b border-zinc-800/70 px-3 py-2 last:border-b-0 ${
                item.id === activeId ? 'bg-zinc-800/50' : 'bg-zinc-900/30'
              }`}
            >
              <span className="w-4 shrink-0 text-right font-mono text-[10px] text-zinc-600">{i + 1}</span>
              <span className={`h-1.5 w-1.5 shrink-0 rounded-full ${DECISION_DOTS[item.decision]}`} aria-hidden />
              <span className="min-w-0 flex-1 truncate text-[12.5px] text-zinc-300">{item.label}</span>
              <span className="shrink-0 font-mono text-[10px] text-zinc-500">
                {item.impact}/{item.cost}
              </span>
              <SeverityBadge value={item.stage} />
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
