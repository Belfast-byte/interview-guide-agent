// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { adaptiveInterviewApi } from '../../api/adaptiveInterview';
import type { AdaptiveAssessmentReport } from '../../types/adaptiveInterview';
import InterviewReportPage from './InterviewReportPage';

vi.mock('../../api/adaptiveInterview', () => ({ adaptiveInterviewApi: { getReport: vi.fn() } }));
beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);
function mount(report: AdaptiveAssessmentReport) {
  vi.mocked(adaptiveInterviewApi.getReport).mockResolvedValue(report);
  render(<MemoryRouter initialEntries={['/report/audit']}><Routes>
    <Route path="/report/:sessionId" element={<InterviewReportPage />} />
  </Routes></MemoryRouter>);
}
it('未考察与合法 L0 空证据在报告中分别展示', async () => {
  mount({ sessionId: 'audit', dimensions: [
    { order: 0, dimension: '数据库', focus: '索引', depthLevel: null, confidence: null,
      rationale: '未考察：本场没有该维度的作答评估', evidences: [] },
    { order: 1, dimension: '缓存', focus: '一致性', depthLevel: 'L0', confidence: 0.9,
      rationale: '回答不知道', evidences: [] },
  ], weakPoints: [], practiceRecommendations: [] });
  const unknown = (await screen.findByRole('heading', { name: '数据库' })).closest('section')!;
  expect(within(unknown).getByText('未考察，不作评级')).toBeTruthy();
  expect(within(unknown).queryByRole('img')).toBeNull();
  expect(within(unknown).queryByText(/置信度/)).toBeNull();
  const l0 = screen.getByRole('heading', { name: '缓存' }).closest('section')!;
  expect(within(l0).getByRole('img', { name: '深度等级 L0 尚无证据' })).toBeTruthy();
  expect(within(l0).getByText('置信度 90%')).toBeTruthy();
});
it('全部未考察时不宣称没有薄弱点', async () => {
  mount({ sessionId: 'audit', dimensions: [
    { order: 0, dimension: '数据库', focus: '索引', depthLevel: null, confidence: null,
      rationale: '未考察', evidences: [] },
  ], weakPoints: [], practiceRecommendations: [] });
  await screen.findByText(/现有评估不足以判断/);
  expect(screen.queryByText(/没有暴露明显薄弱点/)).toBeNull();
  expect(screen.queryByText(/NaN/)).toBeNull();
});
