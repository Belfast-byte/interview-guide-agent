// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { CandidateMemoryEpisode, CandidateMemoryEpisodePage } from '../../types/candidateMemory';
import EpisodeMemoryList from './EpisodeMemoryList';

afterEach(cleanup);

const episode: CandidateMemoryEpisode = {
  episodeId: 1, sessionId: 'practice', turnIndex: 2, sessionMode: 'PRACTICE', assessmentId: 2,
  topic: { skillId: 'java-backend', focusId: 'concurrency' },
  question: '库存并发扣减时 synchronized 如何实现互斥？', answer: '我只知道同一时刻只有一个线程进入。',
  depthLevel: 'L1', expectedDepth: 'L3', rationaleSummary: '没有解释底层机制',
  gaps: [{ gapId: 3, anchor: '一个线程进入', missingPoint: '需要解释锁竞争机制',
    closedByAssessmentId: null, closureEvidenceQuote: null, closureSummary: null }],
  triggerType: 'AGENT_DECISION', priorTurns: [{ turnIndex: 1, question: '想一下监视器的作用。', answer: '不确定。' }],
  createdAt: '2026-09-06T10:00:00',
};
const page: CandidateMemoryEpisodePage = {
  content: [episode], page: 0, size: 20, totalElements: 21, totalPages: 2, last: false,
};

describe('问答记忆', () => {
  it('展示原问答、既有等级、gap 和提示前文', () => {
    render(<EpisodeMemoryList episodes={page} onPage={vi.fn()} />);
    expect(screen.getByText(episode.question)).toBeTruthy();
    expect(screen.getByText(episode.answer)).toBeTruthy();
    expect(screen.getByText('本次表现 L1')).toBeTruthy();
    expect(screen.getByText('需要解释锁竞争机制')).toBeTruthy();
    expect(screen.getByText(/想一下监视器的作用/)).toBeTruthy();
    expect(screen.queryByText(/整理中|独立掌握|迁移已确认/)).toBeNull();
  });

  it('使用真实分页信息切换历史页', () => {
    const onPage = vi.fn();
    render(<EpisodeMemoryList episodes={page} onPage={onPage} />);
    fireEvent.click(screen.getByRole('button', { name: '下一页' }));
    expect(onPage).toHaveBeenCalledWith(1);
    expect((screen.getByRole('button', { name: '上一页' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('仅在有关闭证据时展示缺口已补充', () => {
    const resolved = { ...episode, gaps: [{ ...episode.gaps[0], closedByAssessmentId: 5,
      closureEvidenceQuote: '竞争失败会进入等待', closureSummary: '已解释竞争过程' }] };
    render(<EpisodeMemoryList episodes={{ ...page, content: [resolved] }} onPage={vi.fn()} />);
    expect(screen.getByText('同场后续回答已补充证据：竞争失败会进入等待')).toBeTruthy();
  });
});

it('Episode 文字追问分别展示原始题目与有来源轮次的先前提交', () => {
  Range.prototype.getClientRects = () => [] as unknown as DOMRectList;
  Range.prototype.getBoundingClientRect = () => new DOMRect();
  const followup: CandidateMemoryEpisode = { ...episode, questionType: 'TEXT', codeTaskTurnIndex: 1,
    codeTask: { initialCode: 'void broken() {}', requirements: ['保持原子性'], assumptions: ['共享库存'] },
    submittedCode: null, priorTurns: [{ turnIndex: 1, question: '修复库存', answer: '', submittedCode: 'void fixed() {}' }] };
  render(<EpisodeMemoryList episodes={{ ...page, content: [followup] }} onPage={vi.fn()} />);
  expect(screen.getByText('第 1 轮提交代码')).toBeTruthy();
  expect(screen.getByText('本轮未提交代码。')).toBeTruthy();
  const original = screen.getByLabelText('原始代码（题目材料）');
  expect(within(original).getByRole('textbox').textContent).toContain('broken');
  expect(within(original).queryByText(/fixed/)).toBeNull();
  expect(screen.queryByText('第 2 轮提交代码')).toBeNull();
});

it('练习 CODE→TEXT→CODE 前文显示文字追问当时已公开的提示', () => {
  const revision: CandidateMemoryEpisode = { ...episode, turnIndex: 3, priorTurns: [
    { turnIndex: 1, question: '修复库存', answer: '', feedbackRationale: '已公开的代码修复反馈' },
    { turnIndex: 2, question: '解释事务边界', answer: '在数据库中扣减', feedbackRationale: '仍需区分事务与隔离范围' },
  ] };
  render(<EpisodeMemoryList episodes={{ ...page, content: [revision] }} onPage={vi.fn()} />);
  expect(screen.getAllByText('当时已公开的反馈')).toHaveLength(2);
  expect(screen.getByText('仍需区分事务与隔离范围')).toBeTruthy();
});
it('评估前文没有公开提示时，不把本次报告理由作为之前的反馈', () => {
  const evaluated: CandidateMemoryEpisode = { ...episode, sessionMode: 'EVALUATION', priorTurns: [
    { turnIndex: 1, question: '解释事务边界', answer: '在数据库中扣减', feedbackRationale: null },
  ] };
  render(<EpisodeMemoryList episodes={{ ...page, content: [evaluated] }} onPage={vi.fn()} />);
  expect(screen.queryByText('当时已公开的反馈')).toBeNull();
  expect(screen.getByText(episode.rationaleSummary!)).toBeTruthy();
});
