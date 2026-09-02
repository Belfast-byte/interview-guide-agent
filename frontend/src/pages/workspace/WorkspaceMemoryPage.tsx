import { AlertCircle, ArrowRight } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { adaptiveInterviewApi } from '../../api/adaptiveInterview';
import { getErrorMessage } from '../../api/request';
import {
  buildEpisodeChains,
  getAbilityLabel,
  getEnrichmentStatusLabel,
  getEpisodeTriggerLabel,
  type CandidateMemoryEpisodeNode,
} from '../../components/candidateMemory/candidateMemoryView';
import { ROUTES } from '../../constants/routes';
import type {
  CandidateMemoryResponse,
  CandidateMemoryStablePattern,
  CandidateMemoryTopic,
  EpisodeAssistanceLevel,
  EpisodeEnrichmentStatus,
  EvaluatedAbility,
  MemoryTagCategory,
  PracticeMastery,
  TransferStatus,
} from '../../types/candidateMemory';
import { formatDateTime } from '../../utils/date';

const LEVELS = ['L0', 'L1', 'L2', 'L3', 'L4'] as const;
const CATEGORY_LABELS: Record<MemoryTagCategory, string> = {
  ERROR_PATTERN: '错误模式',
  ANSWER_HABIT: '回答习惯',
};
const MASTERY_LABELS: Record<PracticeMastery, string> = {
  UNRESOLVED: '尚未解决',
  ASSISTED: '辅助完成',
  INDEPENDENT: '独立完成',
};
const TRANSFER_LABELS: Record<TransferStatus, string> = {
  NOT_REEVALUATED: '待正式复验',
  CONFIRMED: '迁移已确认',
  REGRESSED: '迁移未确认',
};
const ASSISTANCE_LABELS: Record<EpisodeAssistanceLevel, string> = {
  NONE: '无辅助',
  FOLLOW_UP: '追问',
  HINT: '提示',
  TOOL_ASSISTED: '工具辅助',
};

const GREEN = '#2F6B4F';
const TAG_TONES = {
  green: { background: `color-mix(in srgb, ${GREEN} 12%, transparent)`, color: GREEN },
  cinnabar: { background: 'color-mix(in srgb, var(--cinnabar) 10%, transparent)', color: 'var(--cinnabar)' },
  ink: { background: 'color-mix(in srgb, var(--ink) 7%, transparent)', color: 'var(--ink-soft)' },
  gray: { background: 'color-mix(in srgb, var(--wk-muted) 12%, transparent)', color: 'var(--wk-muted)' },
} as const;

const ABILITY_TONES: Record<EvaluatedAbility, keyof typeof TAG_TONES> = {
  WEAK: 'cinnabar',
  COMPETENT: 'ink',
  PROFICIENT: 'green',
};
const MASTERY_TONES: Record<PracticeMastery, keyof typeof TAG_TONES> = {
  UNRESOLVED: 'cinnabar',
  ASSISTED: 'ink',
  INDEPENDENT: 'green',
};
const ENRICHMENT_TONES: Record<EpisodeEnrichmentStatus, keyof typeof TAG_TONES> = {
  COMPLETED: 'green',
  FAILED: 'cinnabar',
  PENDING: 'gray',
  PROCESSING: 'gray',
  LEGACY_UNENRICHED: 'gray',
};

export default function WorkspaceMemoryPage() {
  const [page, setPage] = useState(0);
  const [memory, setMemory] = useState<CandidateMemoryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setMemory(await adaptiveInterviewApi.getCandidateMemory(page));
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="pb-24">
      {/* ===== 页首 ===== */}
      <div className="wk-rise pt-10" style={{ animationDelay: '0.02s' }}>
        <p className="flex items-center gap-3 font-monosc text-[11.5px] tracking-[0.18em] text-cinnabar">
          <span className="h-px w-8 bg-cinnabar" aria-hidden="true" />
          CANDIDATE MEMORY / 候选人记忆
        </p>
        <h1 className="mt-5 font-serifsc text-[32px] font-black leading-[1.25] tracking-wide text-ink sm:text-[40px]">
          面试官记得你说过什么。
        </h1>
        <p className="mt-4 max-w-[44em] text-[15px] leading-7 text-wk-muted">
          正式能力、练习掌握与可追溯的问答经历，分别归档在案。
        </p>
      </div>

      {error && (
        <div className="wk-rise mt-8" style={{ animationDelay: '0.1s' }}>
          <div className="wk-error">
            <AlertCircle className="mt-0.5 h-4 w-4 flex-none" />
            <span>候选人记忆加载失败：{error}</span>
            <button
              type="button"
              onClick={() => void load()}
              className="ml-auto flex-none font-semibold underline"
            >
              重新加载
            </button>
          </div>
        </div>
      )}

      {loading ? (
        <p className="mt-16 font-monosc text-xs tracking-[0.15em] text-wk-muted">载入中…</p>
      ) : !error && memory ? (
        <MemoryContent memory={memory} onPage={setPage} />
      ) : null}
    </div>
  );
}

function MemoryContent(props: {
  memory: CandidateMemoryResponse;
  onPage: (page: number) => void;
}) {
  const memory = props.memory;
  return (
    <>
      {/* 01 双轨能力主题 */}
      <section className="wk-rise mt-12" style={{ animationDelay: '0.1s' }} aria-labelledby="ability-topics-heading">
        <SectionHeading
          id="ability-topics-heading"
          index="01"
          title="双轨能力主题"
          detail={`${memory.topics.length} 个主题`}
        />
        {memory.topics.length === 0 ? (
          <EmptyNote message="完成一场自适应面试后，这里会展示能力主题。" />
        ) : (
          <div className="border-t border-ink">
            {memory.topics.map(topic => (
              <TopicSection key={`${topic.skillId}:${topic.focusId}`} topic={topic} />
            ))}
          </div>
        )}
      </section>

      {/* 02 问答追问链 */}
      <section className="wk-rise mt-14" style={{ animationDelay: '0.18s' }} aria-labelledby="episode-memory-heading">
        <SectionHeading
          id="episode-memory-heading"
          index="02"
          title="问答追问链"
          detail={`${memory.episodes.totalElements} 条记忆`}
        />
        <EpisodeChains episodes={memory.episodes} onPage={props.onPage} />
      </section>
    </>
  );
}

function SectionHeading(props: { id: string; index: string; title: string; detail: string }) {
  return (
    <div className="mb-5 flex items-baseline gap-3.5">
      <span className="font-monosc text-xs tracking-wider text-cinnabar">{props.index}</span>
      <h2 id={props.id} className="font-serifsc text-lg font-bold text-ink">{props.title}</h2>
      <span className="ml-auto font-monosc text-[11px] tracking-wider text-wk-muted">{props.detail}</span>
    </div>
  );
}

function TopicSection({ topic }: { topic: CandidateMemoryTopic }) {
  return (
    <article className="border-b border-line py-7">
      <header className="flex flex-wrap items-baseline gap-x-4 gap-y-2">
        <span className="font-monosc text-[11px] tracking-wider text-cinnabar">{topic.skillId}</span>
        <h3 className="font-serifsc text-[17px] font-bold text-ink">{topic.focusId}</h3>
        <span className="ml-auto flex gap-1.5">
          {topic.evaluation && (
            <span className="wk-tag" style={TAG_TONES[ABILITY_TONES[topic.evaluation.ability]]}>
              正式 · {getAbilityLabel(topic.evaluation.ability)}
            </span>
          )}
          {topic.practice && (
            <span className="wk-tag" style={TAG_TONES[MASTERY_TONES[topic.practice.mastery]]}>
              练习 · {MASTERY_LABELS[topic.practice.mastery]}
            </span>
          )}
        </span>
      </header>
      <div className="mt-6 grid gap-8 md:grid-cols-2">
        <EvaluationTrack topic={topic} />
        <PracticeTrack topic={topic} />
      </div>
    </article>
  );
}

function EvaluationTrack({ topic }: { topic: CandidateMemoryTopic }) {
  const track = topic.evaluation;
  if (!track) return <EmptyTrack title="正式能力" />;
  return (
    <section>
      <TrackTitle title="正式能力" />
      {/* 等级计数：mono 数据行 */}
      <div className="mt-4 grid grid-cols-5 gap-2" aria-label="正式能力等级计数">
        {LEVELS.map((level, index) => (
          <div key={level} className="border-t border-line pt-2">
            <p className="font-monosc text-[10px] tracking-wider text-wk-muted">{level}</p>
            <p className="mt-1 font-monosc text-[15px] font-medium text-ink">
              {track.statistics.levelCounts[index] ?? 0}
            </p>
          </div>
        ))}
      </div>
      <PatternList patterns={track.metadata.stablePatterns} />
    </section>
  );
}

function PracticeTrack({ topic }: { topic: CandidateMemoryTopic }) {
  const track = topic.practice;
  if (!track) return <EmptyTrack title="练习掌握" />;
  const result = track.details.latest.result;
  return (
    <section>
      <TrackTitle title="练习掌握" />
      <dl className="mt-4 grid grid-cols-2 gap-x-6 gap-y-3">
        <TrackFact label="最近辅助" value={ASSISTANCE_LABELS[result.assistance]} />
        <TrackFact label="目标深度" value={result.targetDepth} />
        <TrackFact label="未解决次数" value={`${track.details.statistics.unresolvedCount}`} />
        <TrackFact label="能力迁移" value={TRANSFER_LABELS[track.details.transfer.status]} />
      </dl>
      <PatternList patterns={track.metadata.stablePatterns} />
    </section>
  );
}

function TrackTitle({ title }: { title: string }) {
  return (
    <h4 className="font-monosc text-[11px] uppercase tracking-[0.12em] text-wk-muted">{title}</h4>
  );
}

function TrackFact({ label, value }: { label: string; value: string }) {
  return (
    <div className="border-b border-dashed border-line pb-2">
      <dt className="font-monosc text-[10px] tracking-wider text-wk-muted">{label}</dt>
      <dd className="mt-1 text-[13.5px] font-medium text-ink">{value}</dd>
    </div>
  );
}

function EmptyTrack({ title }: { title: string }) {
  return (
    <section>
      <TrackTitle title={title} />
      <p className="mt-4 text-[13px] text-wk-muted">暂无记录</p>
    </section>
  );
}

function PatternList({ patterns }: { patterns: CandidateMemoryStablePattern[] }) {
  if (patterns.length === 0) return null;
  return (
    <div className="mt-5 border-t border-dashed border-line pt-3.5">
      <p className="font-monosc text-[10px] tracking-[0.12em] text-wk-muted">稳定模式</p>
      <div className="mt-2 flex flex-wrap gap-1.5">
        {patterns.map(pattern => (
          <span key={`${pattern.category}:${pattern.tag}`} className="wk-tag" style={TAG_TONES.gray}>
            {CATEGORY_LABELS[pattern.category]} · {pattern.tag} × {pattern.episodeCount}
          </span>
        ))}
      </div>
    </div>
  );
}

function EpisodeChains(props: {
  episodes: CandidateMemoryResponse['episodes'];
  onPage: (page: number) => void;
}) {
  const chains = buildEpisodeChains(props.episodes.content, props.episodes.ancestors);
  if (chains.length === 0) {
    return <EmptyNote message="暂无问答记忆" />;
  }
  return (
    <>
      <div className="border-t border-ink">
        {chains.map(root => (
          <EpisodeNode
            key={`${root.episode.sessionId}:${root.episode.turnIndex}`}
            node={root}
          />
        ))}
      </div>
      <div className="mt-8 flex items-center justify-between">
        <p className="font-monosc text-[11px] tracking-wider text-wk-muted">
          共 {props.episodes.totalElements} 条 · 第 {props.episodes.page + 1} / {Math.max(props.episodes.totalPages, 1)} 页
        </p>
        <div className="flex gap-2">
          <button
            type="button"
            disabled={props.episodes.page === 0}
            onClick={() => props.onPage(props.episodes.page - 1)}
            className="wk-btn-ghost"
          >
            <ArrowRight className="h-3 w-3 rotate-180" />
            上一页
          </button>
          <button
            type="button"
            disabled={props.episodes.last}
            onClick={() => props.onPage(props.episodes.page + 1)}
            className="wk-btn-ghost"
          >
            下一页
            <ArrowRight className="h-3 w-3" />
          </button>
        </div>
      </div>
    </>
  );
}

function EpisodeNode(props: { node: CandidateMemoryEpisodeNode }) {
  const episode = props.node.episode;
  const followUp = episode.triggerType !== 'PLANNED';
  return (
    <div className={followUp ? 'ml-5 border-l border-dashed border-line pl-5 sm:ml-8' : ''}>
      <article className="flex flex-wrap items-baseline gap-x-5 gap-y-1.5 border-b border-dashed border-line py-4">
        <span className="font-monosc text-[11px] tracking-wider text-cinnabar">
          {episode.skillId} / {episode.focusId}
        </span>
        <span className="text-[13.5px] font-medium text-ink">
          {getEpisodeTriggerLabel(episode.triggerType)} · 第 {episode.turnIndex} 轮 · {episode.depthLevel}
        </span>
        {props.node.contextOnly && (
          <span className="font-monosc text-[10px] tracking-wider text-wk-muted">跨页链路上文</span>
        )}
        <span className="ml-auto flex items-center gap-3">
          <span className="hidden font-monosc text-[10.5px] tracking-wider text-wk-muted md:inline">
            {formatDateTime(episode.createdAt)} · 会话 {episode.sessionId.slice(0, 8)}
          </span>
          <span className="wk-tag" style={TAG_TONES[ENRICHMENT_TONES[episode.enrichmentStatus]]}>
            {getEnrichmentStatusLabel(episode.enrichmentStatus)}
          </span>
        </span>
      </article>
      {props.node.children.length > 0 && (
        <div>
          {props.node.children.map(child => (
            <EpisodeNode
              key={`${child.episode.sessionId}:${child.episode.turnIndex}`}
              node={child}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function EmptyNote({ message }: { message: string }) {
  return (
    <div className="border-t border-ink pt-6">
      <p className="max-w-[36em] text-[15px] leading-7 text-wk-muted">{message}</p>
      <Link to={ROUTES.workspace} className="wk-btn-ghost mt-4">
        去安排一场面试
        <ArrowRight className="h-3 w-3" />
      </Link>
    </div>
  );
}
