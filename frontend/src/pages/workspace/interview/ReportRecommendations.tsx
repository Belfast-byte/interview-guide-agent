import { Link } from 'react-router-dom';
import { ROUTES } from '../../../constants/routes';
import type { AdaptiveAssessmentReport } from '../../../types/adaptiveInterview';

export default function ReportRecommendations({ report }: { report: AdaptiveAssessmentReport }) {
  return (
        <aside className="space-y-10 lg:sticky lg:top-20">
          {report.weakPoints.length > 0 && (
            <div>
              <p className="wk-label mb-3">薄弱点 · 待补强</p>
              <ul className="border-t border-line">
                {report.weakPoints.map(weakPoint => (
                  <li key={weakPoint.dimension} className="border-b border-dashed border-line py-4">
                    <div className="flex items-baseline justify-between gap-3">
                      <p className="text-sm font-semibold text-ink">{weakPoint.dimension}</p>
                      <span className="font-monosc text-[11px] text-cinnabar">
                        {weakPoint.demonstratedLevel} → {weakPoint.missingLevel}
                      </span>
                    </div>
                    <p className="mt-1.5 text-[13px] leading-6 text-wk-muted">{weakPoint.missingCapability}</p>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {report.practiceRecommendations.length > 0 && (
            <div>
              <p className="wk-label mb-3">练习建议</p>
              <div className="space-y-4">
                {report.practiceRecommendations.map(practice => (
                  <PracticeCard key={practice.questionSourceId} practice={practice} />
                ))}
              </div>
            </div>
          )}

          {report.weakPoints.length === 0 && report.practiceRecommendations.length === 0 && (
            <p className="text-sm leading-6 text-wk-muted">
              {report.dimensions.some(dimension => dimension.depthLevel === null)
                ? '本场包含未考察维度，现有评估不足以判断这些维度的薄弱点。'
                : '已评估维度没有暴露明显薄弱点。'}可以回到 <Link to={ROUTES.workspace} className="text-cinnabar underline">新的面试</Link> 换更深的维度再跑一场。
            </p>
          )}
        </aside>
  );
}

function PracticeCard({ practice }: { practice: AdaptiveAssessmentReport["practiceRecommendations"][number] }) {
  return (
                  <article key={practice.questionSourceId} className="wk-docket">
                    <div className="flex items-center justify-between gap-3">
                      <p className="font-monosc text-[10.5px] uppercase tracking-[0.12em] text-cinnabar">
                        练习 · {practice.dimension}
                      </p>
                      <span className="wk-tag" style={{ background: 'color-mix(in srgb, var(--ink) 7%, transparent)', color: 'var(--ink-soft)' }}>
                        {practice.questionDifficulty}
                      </span>
                    </div>
                    <p className="mt-3 text-sm font-medium leading-7 text-ink">{practice.question}</p>
                    {practice.status === 'COMPLETED' && (
                      <p className="mt-3 font-monosc text-[10.5px] tracking-wider text-[#2F6B4F]">✓ 已完成</p>
                    )}
                  </article>
  );
}
