import type { AuditDecision, AuditSeverity, ComplexityKind } from '../auditTypes';

export const SEVERITY_STYLES: Record<AuditSeverity, string> = {
  P0: 'border-red-400/30 bg-red-400/10 text-red-300',
  P1: 'border-amber-300/30 bg-amber-300/10 text-amber-200',
  P2: 'border-sky-300/25 bg-sky-300/10 text-sky-200',
  P3: 'border-zinc-500/30 bg-zinc-500/10 text-zinc-400',
};

export const DECISION_STYLES: Record<AuditDecision, string> = {
  DELETE: 'border-rose-400/30 bg-rose-400/10 text-rose-300',
  SIMPLIFY: 'border-amber-300/30 bg-amber-300/10 text-amber-200',
  KEEP: 'border-emerald-400/30 bg-emerald-400/10 text-emerald-300',
  REDESIGN: 'border-sky-400/30 bg-sky-400/10 text-sky-300',
};

export const DECISION_DOTS: Record<AuditDecision, string> = {
  DELETE: 'bg-rose-400',
  SIMPLIFY: 'bg-amber-300',
  KEEP: 'bg-emerald-400',
  REDESIGN: 'bg-sky-400',
};

export const KIND_META: Record<ComplexityKind, { label: string; className: string }> = {
  accidental: { label: '偶然复杂度', className: 'border-rose-400/30 bg-rose-400/10 text-rose-300' },
  essential: { label: '必要复杂度', className: 'border-emerald-400/30 bg-emerald-400/10 text-emerald-300' },
  mixed: { label: '必要 + 偶然交织', className: 'border-zinc-500/40 bg-zinc-500/10 text-zinc-400' },
};

export function SeverityBadge({ value, label }: { value: AuditSeverity; label?: string }) {
  return (
    <span
      className={`inline-flex shrink-0 items-center rounded border px-1.5 py-px font-mono text-[10px] font-medium tracking-wide ${SEVERITY_STYLES[value]}`}
    >
      {label ?? value}
    </span>
  );
}

export function DecisionBadge({ value }: { value: AuditDecision }) {
  return (
    <span
      className={`inline-flex shrink-0 items-center gap-1 rounded border px-1.5 py-px font-mono text-[10px] font-medium tracking-wide ${DECISION_STYLES[value]}`}
    >
      <span className={`h-1 w-1 rounded-full ${DECISION_DOTS[value]}`} />
      {value}
    </span>
  );
}

export function KindBadge({ value }: { value: ComplexityKind }) {
  const meta = KIND_META[value];
  return (
    <span className={`inline-flex shrink-0 items-center rounded border px-1.5 py-px text-[10px] ${meta.className}`}>
      {meta.label}
    </span>
  );
}

export function SectionHeading({
  eyebrow,
  title,
  description,
}: {
  eyebrow: string;
  title: string;
  description?: string;
}) {
  return (
    <div className="mb-5">
      <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-zinc-500">{eyebrow}</div>
      <h2 className="mt-1.5 text-lg font-semibold tracking-tight text-zinc-100">{title}</h2>
      {description && (
        <p className="mt-1.5 max-w-3xl text-[13px] leading-relaxed text-zinc-400">{description}</p>
      )}
    </div>
  );
}
