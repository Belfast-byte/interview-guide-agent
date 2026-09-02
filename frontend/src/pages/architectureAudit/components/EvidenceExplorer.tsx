import { useMemo, useState } from 'react';
import { ChevronDown, Search } from 'lucide-react';
import type { AuditSeverity, CodeEvidence, RootCause } from '../auditTypes';
import { SectionHeading, SeverityBadge } from './ui';

const SEVERITY_FILTERS: readonly (AuditSeverity | 'ALL')[] = ['ALL', 'P0', 'P1', 'P2', 'P3'];

interface Props {
  evidence: readonly CodeEvidence[];
  causes: readonly RootCause[];
  rootCauseFilter: string;
  onRootCauseFilterChange: (id: string) => void;
}

export default function EvidenceExplorer({ evidence, causes, rootCauseFilter, onRootCauseFilterChange }: Props) {
  const [query, setQuery] = useState('');
  const [severity, setSeverity] = useState<AuditSeverity | 'ALL'>('ALL');
  const [openIds, setOpenIds] = useState<ReadonlySet<string>>(new Set(['E-01']));

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return evidence.filter(item => {
      if (rootCauseFilter !== 'ALL' && item.rootCauseId !== rootCauseFilter) return false;
      if (severity !== 'ALL' && item.severity !== severity) return false;
      if (!q) return true;
      const haystack = [item.title, item.finding, item.impact, item.fix, item.tags.join(' '), item.refs.join(' ')]
        .join(' ')
        .toLowerCase();
      return haystack.includes(q);
    });
  }, [evidence, query, severity, rootCauseFilter]);

  const toggle = (id: string) => {
    setOpenIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  return (
    <section id="evidence" className="scroll-mt-8">
      <SectionHeading
        eyebrow="Evidence"
        title="代码证据浏览器"
        description="每条证据的引用行号已按当前生产代码复核。默认只展开第一条，点击卡片查看发现、影响与修法。"
      />

      <div className="mb-3 flex flex-col gap-2">
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-zinc-600" />
          <input
            type="search"
            value={query}
            onChange={e => setQuery(e.target.value)}
            placeholder="搜索 ActionIntent、race、prompt…"
            className="w-full rounded-md border border-zinc-800 bg-zinc-900/50 py-2 pl-9 pr-3 text-[13px] text-zinc-200 placeholder:text-zinc-600 focus:border-zinc-600 focus:outline-none"
          />
        </div>
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="mr-1 font-mono text-[10px] uppercase tracking-[0.18em] text-zinc-600">严重度</span>
          {SEVERITY_FILTERS.map(value => (
            <button
              key={value}
              type="button"
              aria-pressed={severity === value}
              onClick={() => setSeverity(value)}
              className={`rounded border px-2 py-0.5 font-mono text-[11px] transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-zinc-400 ${
                severity === value
                  ? 'border-zinc-600 bg-zinc-800 text-zinc-100'
                  : 'border-zinc-800 text-zinc-500 hover:text-zinc-300'
              }`}
            >
              {value === 'ALL' ? '全部' : value}
            </button>
          ))}
          <span className="ml-3 mr-1 font-mono text-[10px] uppercase tracking-[0.18em] text-zinc-600">根因</span>
          {[{ id: 'ALL', index: '全部' }, ...causes].map(cause => (
            <button
              key={cause.id}
              type="button"
              aria-pressed={rootCauseFilter === cause.id}
              onClick={() => onRootCauseFilterChange(cause.id)}
              className={`rounded border px-2 py-0.5 font-mono text-[11px] transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-zinc-400 ${
                rootCauseFilter === cause.id
                  ? 'border-zinc-600 bg-zinc-800 text-zinc-100'
                  : 'border-zinc-800 text-zinc-500 hover:text-zinc-300'
              }`}
            >
              {'index' in cause && cause.id !== 'ALL' ? cause.index : '全部'}
            </button>
          ))}
        </div>
      </div>

      <div className="mb-2 font-mono text-[11px] text-zinc-600">
        {visible.length} / {evidence.length} 条证据
      </div>

      <div className="space-y-2">
        {visible.map(item => {
          const isOpen = openIds.has(item.id);
          return (
            <article
              key={item.id}
              className="audit-evidence-card overflow-hidden rounded-lg border border-zinc-800 bg-zinc-900/30"
            >
              <button
                type="button"
                aria-expanded={isOpen}
                onClick={() => toggle(item.id)}
                className="flex w-full items-center gap-3 px-4 py-2.5 text-left transition-colors hover:bg-zinc-800/30 focus-visible:outline focus-visible:outline-2 focus-visible:outline-zinc-400"
              >
                <SeverityBadge value={item.severity} label={item.severityLabel} />
                <span className="shrink-0 font-mono text-[11px] text-zinc-500">{item.id}</span>
                <span className="min-w-0 flex-1 truncate text-[13px] font-medium text-zinc-200">{item.title}</span>
                <span className="hidden gap-1 md:flex">
                  {item.tags.slice(0, 3).map(tag => (
                    <span key={tag} className="rounded border border-zinc-800 px-1.5 py-px font-mono text-[10px] text-zinc-500">
                      {tag}
                    </span>
                  ))}
                </span>
                <ChevronDown className={`h-3.5 w-3.5 shrink-0 text-zinc-500 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
              </button>
              {isOpen && (
                <div className="border-t border-zinc-800/70 px-4 py-3">
                  <dl className="grid gap-3 text-[12.5px] leading-relaxed md:grid-cols-3">
                    <div>
                      <dt className="font-mono text-[10px] uppercase tracking-[0.18em] text-zinc-500">发现</dt>
                      <dd className="mt-1 text-zinc-300">{item.finding}</dd>
                    </div>
                    <div>
                      <dt className="font-mono text-[10px] uppercase tracking-[0.18em] text-zinc-500">实际影响</dt>
                      <dd className="mt-1 text-zinc-300">{item.impact}</dd>
                    </div>
                    <div>
                      <dt className="font-mono text-[10px] uppercase tracking-[0.18em] text-zinc-500">最小修法</dt>
                      <dd className="mt-1 text-sky-100/80">{item.fix}</dd>
                    </div>
                  </dl>
                  <div className="mt-3 space-y-1 border-t border-zinc-800/70 pt-2.5">
                    {item.refs.map(ref => (
                      <div key={ref} className="break-all font-mono text-[11px] text-zinc-500">
                        {ref}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </article>
          );
        })}
        {visible.length === 0 && (
          <div className="rounded-lg border border-dashed border-zinc-800 px-4 py-8 text-center text-[13px] text-zinc-500">
            没有匹配的证据——尝试放宽严重度或根因过滤。
          </div>
        )}
      </div>
    </section>
  );
}
