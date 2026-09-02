import { useEffect, useState } from 'react';

export const AUDIT_SECTIONS = [
  { id: 'verdict', label: '总体结论' },
  { id: 'hotspots', label: '复杂度热点' },
  { id: 'causes', label: '核心问题地图' },
  { id: 'arch', label: 'Before / After' },
  { id: 'decisions', label: '决策账本' },
  { id: 'matrix', label: '影响 × 成本' },
  { id: 'roadmap', label: '重构路线图' },
  { id: 'evidence', label: '代码证据' },
  { id: 'appendix', label: '审计附录' },
] as const;

function useScrollSpy() {
  const [active, setActive] = useState<string>(AUDIT_SECTIONS[0].id);
  useEffect(() => {
    const observer = new IntersectionObserver(
      entries => {
        for (const entry of entries) {
          if (entry.isIntersecting) setActive(entry.target.id);
        }
      },
      { rootMargin: '-15% 0px -70% 0px' },
    );
    for (const section of AUDIT_SECTIONS) {
      const el = document.getElementById(section.id);
      if (el) observer.observe(el);
    }
    return () => observer.disconnect();
  }, []);
  return active;
}

function RailLinks({ active, onNavigate }: { active: string; onNavigate?: () => void }) {
  return (
    <>
      {AUDIT_SECTIONS.map((section, i) => {
        const isActive = section.id === active;
        return (
          <a
            key={section.id}
            href={`#${section.id}`}
            onClick={onNavigate}
            className={`group flex shrink-0 items-baseline gap-2.5 whitespace-nowrap rounded px-2 py-1.5 text-[12.5px] transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-zinc-400 ${
              isActive ? 'bg-zinc-800/70 text-zinc-100' : 'text-zinc-500 hover:text-zinc-300'
            }`}
          >
            <span className={`font-mono text-[10px] ${isActive ? 'text-zinc-300' : 'text-zinc-600'}`}>
              {String(i + 1).padStart(2, '0')}
            </span>
            {section.label}
          </a>
        );
      })}
    </>
  );
}

export default function AuditRail() {
  const active = useScrollSpy();
  return (
    <>
      {/* 桌面：吸顶侧栏 */}
      <aside className="hidden lg:block">
        <div className="sticky top-8">
          <div className="mb-4 px-2">
            <div className="font-mono text-[10px] uppercase tracking-[0.22em] text-zinc-600">Audit</div>
            <div className="mt-1 text-[13px] font-semibold text-zinc-200">复杂度审计</div>
            <div className="mt-0.5 font-mono text-[10px] text-zinc-600">2026-08-29</div>
          </div>
          <nav aria-label="审计章节" className="flex flex-col gap-0.5">
            <RailLinks active={active} />
          </nav>
        </div>
      </aside>
      {/* 移动：顶部横向导航 */}
      <nav
        aria-label="审计章节"
        className="sticky top-0 z-20 -mx-4 flex gap-1 overflow-x-auto border-b border-zinc-800/80 bg-[#09090b]/95 px-4 py-2 backdrop-blur lg:hidden"
      >
        <RailLinks active={active} />
      </nav>
    </>
  );
}
