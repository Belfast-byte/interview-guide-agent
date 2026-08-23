import { AlertCircle, CheckCircle2, Clock3, GitBranch, Loader2 } from 'lucide-react';
import type {
  CandidateMemoryEpisodePage,
  EpisodeEnrichmentStatus,
} from '../../types/candidateMemory';
import { formatDateTime } from '../../utils/date';
import {
  buildEpisodeChains,
  getEnrichmentStatusLabel,
  type CandidateMemoryEpisodeNode,
} from './candidateMemoryView';

export default function EpisodeMemoryList(props: {
  episodes: CandidateMemoryEpisodePage;
  onPage: (page: number) => void;
}) {
  const chains = buildEpisodeChains(props.episodes.content);
  if (chains.length === 0) {
    return <p className="rounded-2xl border border-dashed border-slate-300 bg-white/70 p-8 text-center text-sm text-slate-500 dark:border-slate-700 dark:bg-slate-900/70">暂无问答记忆</p>;
  }
  return (
    <>
      <div className="space-y-4">
        {chains.map(root => (
          <EpisodeNode
            key={`${root.episode.sessionId}:${root.episode.turnIndex}`}
            node={root}
            followUp={root.episode.parentTurnIndex !== null}
          />
        ))}
      </div>
      <EpisodePagination episodes={props.episodes} onPage={props.onPage} />
    </>
  );
}

function EpisodeNode(props: { node: CandidateMemoryEpisodeNode; followUp: boolean }) {
  const episode = props.node.episode;
  return (
    <div className={props.followUp ? 'ml-5 border-l-2 border-primary-200 pl-4 sm:ml-8 dark:border-primary-900' : ''}>
      <article className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="text-xs font-semibold text-primary-600 dark:text-primary-300">
              {episode.skillId} / {episode.focusId}
            </p>
            <p className="mt-1 text-sm font-bold text-slate-800 dark:text-slate-100">
              {props.followUp && <GitBranch className="mr-1.5 inline h-4 w-4" />}
              {props.followUp ? '追问' : '起始问题'} · 第 {episode.turnIndex} 轮 · {episode.depthLevel}
            </p>
          </div>
          <EnrichmentStatus status={episode.enrichmentStatus} />
        </div>
        <p className="mt-3 text-xs text-slate-400">
          {formatDateTime(episode.createdAt)} · 会话 {episode.sessionId}
        </p>
      </article>
      {props.node.children.length > 0 && (
        <div className="mt-3 space-y-3">
          {props.node.children.map(child => (
            <EpisodeNode
              key={`${child.episode.sessionId}:${child.episode.turnIndex}`}
              node={child}
              followUp
            />
          ))}
        </div>
      )}
    </div>
  );
}

function EnrichmentStatus({ status }: { status: EpisodeEnrichmentStatus }) {
  const icon = status === 'COMPLETED'
    ? <CheckCircle2 className="h-3.5 w-3.5" />
    : status === 'PROCESSING'
      ? <Loader2 className="h-3.5 w-3.5 animate-spin" />
      : status === 'FAILED'
        ? <AlertCircle className="h-3.5 w-3.5" />
        : <Clock3 className="h-3.5 w-3.5" />;
  const tone = status === 'COMPLETED'
    ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300'
    : status === 'FAILED'
      ? 'bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300'
      : 'bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-300';
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${tone}`}>
      {icon}{getEnrichmentStatusLabel(status)}
    </span>
  );
}

function EpisodePagination(props: {
  episodes: CandidateMemoryEpisodePage;
  onPage: (page: number) => void;
}) {
  const episodes = props.episodes;
  return (
    <div className="mt-5 flex flex-wrap items-center justify-between gap-3 text-xs text-slate-500">
      <p>共 {episodes.totalElements} 条 · 第 {episodes.page + 1} / {Math.max(episodes.totalPages, 1)} 页</p>
      <div className="flex gap-2">
        <button type="button" className="btn-secondary rounded-lg px-3 py-2 disabled:opacity-40" disabled={episodes.page === 0} onClick={() => props.onPage(episodes.page - 1)}>上一页</button>
        <button type="button" className="btn-secondary rounded-lg px-3 py-2 disabled:opacity-40" disabled={episodes.last} onClick={() => props.onPage(episodes.page + 1)}>下一页</button>
      </div>
    </div>
  );
}
