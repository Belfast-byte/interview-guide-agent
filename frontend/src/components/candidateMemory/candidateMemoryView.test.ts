import assert from 'node:assert/strict';
import test from 'node:test';

import type { CandidateMemoryEpisode } from '../../types/candidateMemory.ts';
import {
  buildEpisodeChains,
  getEnrichmentStatusLabel,
} from './candidateMemoryView.ts';

function episode(
  sessionId: string,
  turnIndex: number,
  parentTurnIndex: number | null,
): CandidateMemoryEpisode {
  return {
    sessionId,
    turnIndex,
    parentTurnIndex,
    skillId: 'java',
    focusId: 'concurrency',
    depthLevel: 'L2',
    enrichmentStatus: 'COMPLETED',
    createdAt: `2026-08-23T10:00:0${turnIndex}`,
  };
}

test('按 session 与 parentTurnIndex 组合追问链，不串联不同会话的相同轮次', () => {
  const roots = buildEpisodeChains([
    episode('session-a', 3, 2),
    episode('session-b', 2, 1),
    episode('session-a', 2, 1),
    episode('session-b', 1, null),
    episode('session-a', 1, null),
  ]);

  assert.deepEqual(
    roots.map(root => ({
      sessionId: root.episode.sessionId,
      turns: [
        root.episode.turnIndex,
        ...root.children.map(child => child.episode.turnIndex),
      ],
    })),
    [
      { sessionId: 'session-b', turns: [1, 2] },
      { sessionId: 'session-a', turns: [1, 2] },
    ],
  );
  assert.equal(roots[1]?.children[0]?.children[0]?.episode.turnIndex, 3);
});

test('分页缺少父 Episode 时将当前 Episode 作为独立链根展示', () => {
  const roots = buildEpisodeChains([episode('session-a', 4, 3)]);

  assert.equal(roots.length, 1);
  assert.equal(roots[0]?.episode.turnIndex, 4);
});

test('补全状态明确区分失败、处理中、等待和历史未补全', () => {
  assert.equal(getEnrichmentStatusLabel('COMPLETED'), '已补全');
  assert.equal(getEnrichmentStatusLabel('FAILED'), '补全失败');
  assert.equal(getEnrichmentStatusLabel('PROCESSING'), '补全中');
  assert.equal(getEnrichmentStatusLabel('PENDING'), '等待补全');
  assert.equal(getEnrichmentStatusLabel('LEGACY_UNENRICHED'), '历史数据未补全');
});
