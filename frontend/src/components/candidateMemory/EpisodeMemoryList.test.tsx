// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type {
  CandidateMemoryEpisode,
  CandidateMemoryEpisodePage,
  CandidateMemoryTurnTriggerType,
  EpisodeEnrichmentStatus,
} from '../../types/candidateMemory';
import EpisodeMemoryList from './EpisodeMemoryList';

const SESSION_ID = 'session-memory';

afterEach(cleanup);

function episode(input: {
  turnIndex: number;
  parentTurnIndex: number | null;
  triggerType: CandidateMemoryTurnTriggerType;
  enrichmentStatus: EpisodeEnrichmentStatus;
}): CandidateMemoryEpisode {
  return {
    sessionId: SESSION_ID,
    turnIndex: input.turnIndex,
    parentTurnIndex: input.parentTurnIndex,
    triggerType: input.triggerType,
    skillId: 'java-backend',
    focusId: 'concurrency',
    depthLevel: 'L2',
    enrichmentStatus: input.enrichmentStatus,
    createdAt: `2026-08-23T10:00:0${input.turnIndex}`,
  };
}

function page(
  content: CandidateMemoryEpisode[],
  ancestors: CandidateMemoryEpisode[] = [],
): CandidateMemoryEpisodePage {
  return {
    content,
    ancestors,
    page: 1,
    size: 20,
    totalElements: content.length,
    totalPages: 2,
    last: true,
  };
}

describe('EpisodeMemoryList', () => {
  it('组合跨页祖先和当前追问，并展示真实触发来源', () => {
    const root = episode({
      turnIndex: 1,
      parentTurnIndex: null,
      triggerType: 'PLANNED',
      enrichmentStatus: 'COMPLETED',
    });
    const followUp = episode({
      turnIndex: 2,
      parentTurnIndex: 1,
      triggerType: 'ASSESSMENT_GAP',
      enrichmentStatus: 'PROCESSING',
    });

    render(<EpisodeMemoryList episodes={page([followUp], [root])} onPage={vi.fn()} />);

    expect(screen.getByText(/起始问题 · 第 1 轮/)).toBeTruthy();
    expect(screen.getByText(/能力缺口追问 · 第 2 轮/)).toBeTruthy();
    expect(screen.getByText('跨页链路上文')).toBeTruthy();
    expect(screen.getByText('已补全')).toBeTruthy();
    expect(screen.getByText('补全中')).toBeTruthy();
  });

  it.each([
    ['PENDING', '等待补全'],
    ['FAILED', '补全失败'],
    ['LEGACY_UNENRICHED', '历史数据未补全'],
  ] as const)('展示 %s 补全状态', (status, label) => {
    const memory = episode({
      turnIndex: 1,
      parentTurnIndex: null,
      triggerType: 'TOOL_RESULT',
      enrichmentStatus: status,
    });

    render(<EpisodeMemoryList episodes={page([memory])} onPage={vi.fn()} />);

    expect(screen.getByText(label)).toBeTruthy();
    expect(screen.getByText(/工具结果追问 · 第 1 轮/)).toBeTruthy();
  });
});
