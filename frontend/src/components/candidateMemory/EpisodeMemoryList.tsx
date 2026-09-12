import CodeRepairHistory from '../codeRepair/CodeRepairHistory';
import type { CandidateMemoryEpisode, CandidateMemoryEpisodePage } from '../../types/candidateMemory';
import { formatDateTime } from '../../utils/date';

export default function EpisodeMemoryList(props: {
  episodes: CandidateMemoryEpisodePage;
  onPage: (page: number) => void;
}) {
  const { episodes, onPage } = props;
  if (episodes.totalElements === 0) {
    return <p className="py-8 text-sm text-wk-muted">暂无问答记忆，完成一次作答后会显示在这里。</p>;
  }
  return (
    <>
      <div className="space-y-5">
        {episodes.content.map(episode => (
          <article key={episode.episodeId} className="rounded-lg border border-line p-5">
            <p className="mb-3 text-xs text-wk-muted">{episode.topic.skillId} / {episode.topic.focusId}</p>
            <EpisodeMemoryDetail episode={episode} />
          </article>
        ))}
      </div>
      <div className="mt-5 flex items-center justify-between gap-3 text-xs text-wk-muted">
        <p>共 {episodes.totalElements} 条 · 第 {episodes.page + 1} / {episodes.totalPages} 页</p>
        <div className="flex gap-3">
          <button type="button" disabled={episodes.page === 0} onClick={() => onPage(episodes.page - 1)} className="disabled:opacity-40">上一页</button>
          <button type="button" disabled={episodes.last} onClick={() => onPage(episodes.page + 1)} className="disabled:opacity-40">下一页</button>
        </div>
      </div>
    </>
  );
}

export function EpisodeMemoryDetail({ episode }: { episode: CandidateMemoryEpisode }) {
  return (
    <div className="space-y-3 text-sm">
      <div className="flex flex-wrap items-center gap-3">
        <span className="wk-tag">本次表现 {episode.depthLevel}</span>
        {episode.expectedDepth && <span className="text-xs text-wk-muted">当次目标 {episode.expectedDepth}</span>}
        <span className="text-xs text-wk-muted">{formatDateTime(episode.createdAt)} · 第 {episode.turnIndex} 轮</span>
      </div>
      <p className="font-medium text-ink">{episode.question}</p>
      <p className="text-wk-muted">{episode.rationaleSummary}</p>
      <EpisodeGaps episode={episode} />
      <details className="rounded-md bg-black/[0.02] p-3">
        <summary className="cursor-pointer text-ink-soft">查看原回答与当时前文</summary>
        {episode.priorTurns.length > 0 && (
          <div className="mt-3 border-l-2 border-line pl-3 text-wk-muted">
            <p className="mb-2 text-xs">这次回答有前文，判断表现时需结合当时的追问与提示。</p>
            {episode.priorTurns.map(turn => (
              <div key={turn.turnIndex} className="mb-3 whitespace-pre-wrap">
                <p>第 {turn.turnIndex} 轮：{turn.question}</p><p className="mt-1">回答：{turn.answer}</p>
                {turn.feedbackRationale != null && <div className="mt-2 border-l-2 border-line pl-3">
                  <p className="text-xs">当时已公开的反馈</p><p className="mt-1 text-ink-soft">{turn.feedbackRationale}</p>
                </div>}
                <CodeRepairHistory turn={turn} />
              </div>
            ))}
          </div>
        )}
        <p className="mt-3 whitespace-pre-wrap leading-7 text-ink-soft">{episode.answer}</p>
        <CodeRepairHistory turn={episode} />
      </details>
      <p className="text-xs text-wk-muted">{episode.sessionMode === 'PRACTICE' ? '练习' : '评估'} · 来源会话 {episode.sessionId}</p>
    </div>
  );
}

function EpisodeGaps({ episode }: { episode: CandidateMemoryEpisode }) {
  return (
    <ul className="space-y-2">
      {episode.gaps.map(gap => (
        <li key={gap.gapId} className="border-l-2 border-line pl-3 text-wk-muted">
          <p>{gap.missingPoint}</p>
          {gap.closureEvidenceQuote && (
            <div className="mt-1 text-xs">
              <p>同场后续回答已补充证据：{gap.closureEvidenceQuote}</p>
              <p>{gap.closureSummary}</p>
            </div>
          )}
        </li>
      ))}
    </ul>
  );
}
