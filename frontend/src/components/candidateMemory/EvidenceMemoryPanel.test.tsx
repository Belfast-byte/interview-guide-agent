// @vitest-environment jsdom
import { render, screen, cleanup } from '@testing-library/react';
import { afterEach, describe, it, expect } from 'vitest';
import EvidenceMemoryPanel from './EvidenceMemoryPanel';
import type { CapabilityBelief } from '../../types/candidateMemory';

afterEach(cleanup);
const belief: CapabilityBelief = {
  capabilityKey: 'atomicity', objective: '区分可见性与原子性',
  state: 'CORRECTED_WITH_ASSISTANCE', needsVerification: true,
  independentOpportunities: 1, revision: 'v1', evidenceRevisionIds: [1, 2],
  latestObservation: '获得操作分解线索后能解释竞态。', updatedAt: '2026-09-05',
};
describe('evidence memory', () => {
  it('distinguishes assisted correction from independent evidence', () => {
    const { rerender } = render(<EvidenceMemoryPanel beliefs={[belief]} />);
    expect(screen.getByText('提示后修正，独立应用待验证')).toBeTruthy();
    expect(screen.queryByText('已有独立应用证据')).toBeNull();
    rerender(<EvidenceMemoryPanel beliefs={[{ ...belief, state: 'INDEPENDENTLY_DEMONSTRATED', needsVerification: false }]} />);
    expect(screen.getByText('已有独立应用证据')).toBeTruthy();
    expect(screen.queryByText('后续练习将尝试换一个场景，在没有提示时验证。')).toBeNull();
  });
  it('does not turn missing observations into weakness', () => {
    render(<EvidenceMemoryPanel />);
    expect(screen.getByText(/暂无经过证据整理/)).toBeTruthy();
    expect(screen.queryByText('需要进一步验证')).toBeNull();
  });
});
