import { useMemo, useState } from 'react';
import type { FactSourceRow, StateMachineRow, ValidationRow } from '../auditTypes';
import { SectionHeading } from './ui';

interface Props {
  stateMachines: readonly StateMachineRow[];
  factSources: readonly FactSourceRow[];
  validationMatrix: readonly ValidationRow[];
}

const TH = 'px-3 py-2 font-mono text-[10px] font-normal uppercase tracking-[0.16em] text-zinc-500';
const TD = 'border-t border-zinc-800/60 px-3 py-2 align-top text-[12px] leading-relaxed text-zinc-400';
const TD_FIRST = `${TD} font-medium text-zinc-200`;

function matches(query: string, fields: readonly string[]) {
  if (!query) return true;
  const q = query.toLowerCase();
  return fields.some(f => f.toLowerCase().includes(q));
}

export default function AppendixTables({ stateMachines, factSources, validationMatrix }: Props) {
  const [query, setQuery] = useState('');

  const filteredMachines = useMemo(
    () => stateMachines.filter(r => matches(query, [r.name, r.category, r.decision, r.note])),
    [stateMachines, query],
  );
  const filteredFacts = useMemo(
    () => factSources.filter(r => matches(query, [r.fact, r.current, r.recommended, r.removable])),
    [factSources, query],
  );
  const filteredValidations = useMemo(
    () => validationMatrix.filter(r => matches(query, [r.invariant, r.current, r.needed, r.duplicate])),
    [validationMatrix, query],
  );

  return (
    <section id="appendix" className="scroll-mt-8">
      <SectionHeading
        eyebrow="Appendix"
        title="审计附录：三张明细表"
        description="来自审计原文的完整明细，默认折叠。搜索框同时过滤三张表。"
      />
      <input
        type="search"
        value={query}
        onChange={e => setQuery(e.target.value)}
        placeholder="过滤附录表，如 WorkPhase、unique、竞态…"
        className="mb-3 w-full rounded-md border border-zinc-800 bg-zinc-900/50 px-3 py-2 text-[13px] text-zinc-200 placeholder:text-zinc-600 focus:border-zinc-600 focus:outline-none"
      />

      <div className="space-y-2">
        <details className="rounded-lg border border-zinc-800 bg-zinc-900/30" open>
          <summary className="cursor-pointer px-4 py-3 text-[13px] font-medium text-zinc-200 transition-colors hover:text-zinc-50">
            状态机审计
            <span className="ml-2 font-mono text-[10px] text-zinc-600">{filteredMachines.length} / {stateMachines.length}</span>
          </summary>
          <div className="overflow-x-auto border-t border-zinc-800/70">
            <table className="w-full min-w-[640px] text-left">
              <thead>
                <tr>
                  <th className={TH}>状态机</th>
                  <th className={TH}>分类</th>
                  <th className={TH}>判断</th>
                  <th className={TH}>说明</th>
                </tr>
              </thead>
              <tbody>
                {filteredMachines.map(row => (
                  <tr key={row.name}>
                    <td className={`${TD_FIRST} font-mono text-[11.5px]`}>{row.name}</td>
                    <td className={TD}>{row.category}</td>
                    <td className={`${TD} whitespace-nowrap font-mono text-[11px] text-zinc-300`}>{row.decision}</td>
                    <td className={TD}>{row.note}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </details>

        <details className="rounded-lg border border-zinc-800 bg-zinc-900/30">
          <summary className="cursor-pointer px-4 py-3 text-[13px] font-medium text-zinc-200 transition-colors hover:text-zinc-50">
            重复 Source of Truth
            <span className="ml-2 font-mono text-[10px] text-zinc-600">{filteredFacts.length} / {factSources.length}</span>
          </summary>
          <div className="overflow-x-auto border-t border-zinc-800/70">
            <table className="w-full min-w-[720px] text-left">
              <thead>
                <tr>
                  <th className={TH}>事实</th>
                  <th className={TH}>当前存储于</th>
                  <th className={TH}>建议唯一事实源</th>
                  <th className={TH}>可删除 / 降级</th>
                </tr>
              </thead>
              <tbody>
                {filteredFacts.map(row => (
                  <tr key={row.fact}>
                    <td className={TD_FIRST}>{row.fact}</td>
                    <td className={TD}>{row.current}</td>
                    <td className={`${TD} text-emerald-200/80`}>{row.recommended}</td>
                    <td className={`${TD} text-rose-200/70`}>{row.removable}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </details>

        <details className="rounded-lg border border-zinc-800 bg-zinc-900/30">
          <summary className="cursor-pointer px-4 py-3 text-[13px] font-medium text-zinc-200 transition-colors hover:text-zinc-50">
            过度校验与 correctness owner 矩阵
            <span className="ml-2 font-mono text-[10px] text-zinc-600">{filteredValidations.length} / {validationMatrix.length}</span>
          </summary>
          <div className="overflow-x-auto border-t border-zinc-800/70">
            <table className="w-full min-w-[760px] text-left">
              <thead>
                <tr>
                  <th className={TH}>Business invariant</th>
                  <th className={TH}>当前保护机制</th>
                  <th className={TH}>真正必要 / owner</th>
                  <th className={TH}>重复机制或缺口</th>
                </tr>
              </thead>
              <tbody>
                {filteredValidations.map(row => (
                  <tr key={row.invariant}>
                    <td className={TD_FIRST}>{row.invariant}</td>
                    <td className={TD}>{row.current}</td>
                    <td className={`${TD} text-emerald-200/80`}>{row.needed}</td>
                    <td className={`${TD} text-rose-200/70`}>{row.duplicate}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="border-t border-zinc-800/70 px-4 py-2.5 font-mono text-[10px] leading-relaxed text-zinc-600">
            分类口径：A = 有独立 failure scenario 的必要防线 · B = 体验 / 成本 / 可观察性优化 · C = 重复 correctness 保护 · D = 没有当前价值的冗余
          </p>
        </details>
      </div>
    </section>
  );
}
