import type { CapabilityBelief } from '../../types/candidateMemory';

const LABELS: Record<string, string> = {
  NO_EVIDENCE: '暂无充分证据',
  NEEDS_REVALIDATION: '需要进一步验证',
  SELF_CORRECTED: '展开解释后修正，独立应用待验证',
  CORRECTED_WITH_ASSISTANCE: '提示后修正，独立应用待验证',
  INDEPENDENTLY_DEMONSTRATED: '已有独立应用证据',
  MIXED_EVIDENCE: '表现存在分歧，需复核',
};

export default function EvidenceMemoryPanel({ beliefs = [] }: { beliefs?: CapabilityBelief[] }) {
  if (!beliefs.length) {
    return <p className="mt-4 text-sm text-wk-muted">暂无经过证据整理的能力判断，历史记录不直接代表当前掌握情况。</p>;
  }
  return (
    <section className="mt-5 space-y-4" aria-label="基于作答证据的记忆">
      {beliefs.map((belief) => (
        <div key={belief.capabilityKey} className="rounded-lg border border-line p-4">
          <h4 className="text-sm font-semibold text-ink">{belief.objective}</h4>
          <p className="mt-2 text-sm font-medium text-ink">{LABELS[belief.state] ?? '待复核'}</p>
          <p className="mt-2 text-sm text-wk-muted">{belief.latestObservation}</p>
          <p className="mt-2 text-xs text-wk-muted">有效独立作答机会：{belief.independentOpportunities}</p>
          {belief.needsVerification && (
            <p className="mt-2 text-xs text-wk-muted">后续练习将尝试换一个场景，在没有提示时验证。</p>
          )}
        </div>
      ))}
    </section>
  );
}
