import { BrainCircuit, Dumbbell, Tags } from 'lucide-react';
import type {
  CandidateMemoryStablePattern,
  CandidateMemoryTopic,
  EpisodeAssistanceLevel,
  EvaluatedAbility,
  MemoryTagCategory,
  PracticeMastery,
  TransferStatus,
} from '../../types/candidateMemory';
import { getAbilityLabel } from './candidateMemoryView';

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

export default function AbilityTopicCard({ topic }: { topic: CandidateMemoryTopic }) {
  return (
    <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
      <header>
        <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
          <BrainCircuit className="h-4 w-4" />{topic.skillId}
        </div>
        <h3 className="mt-1 text-lg font-bold text-slate-900 dark:text-white">
          {topic.focusId}
        </h3>
      </header>
      <div className="mt-5 grid gap-4 xl:grid-cols-2">
        <EvaluationPanel topic={topic} />
        <PracticePanel topic={topic} />
      </div>
    </article>
  );
}

function EvaluationPanel({ topic }: { topic: CandidateMemoryTopic }) {
  const track = topic.evaluation;
  if (!track) return <EmptyTrack title="正式能力" />;
  return (
    <section className="rounded-xl bg-slate-50 p-4 dark:bg-slate-800/70">
      <div className="flex items-center justify-between gap-3">
        <h4 className="text-sm font-bold text-slate-700 dark:text-slate-200">正式能力</h4>
        <AbilityBadge ability={track.ability} />
      </div>
      <div className="mt-4 grid grid-cols-5 gap-1.5" aria-label="正式能力等级计数">
        {LEVELS.map((level, index) => (
          <div key={level} className="rounded-lg bg-white px-1 py-2 text-center dark:bg-slate-900">
            <p className="text-[11px] font-semibold text-slate-400">{level}</p>
            <p className="mt-1 font-bold text-slate-800 dark:text-slate-100">
              {track.statistics.levelCounts[index] ?? 0}
            </p>
          </div>
        ))}
      </div>
      <PatternList patterns={track.metadata.stablePatterns} />
    </section>
  );
}

function PracticePanel({ topic }: { topic: CandidateMemoryTopic }) {
  const track = topic.practice;
  if (!track) return <EmptyTrack title="练习掌握" />;
  const result = track.details.latest.result;
  return (
    <section className="rounded-xl bg-violet-50/70 p-4 dark:bg-violet-950/20">
      <div className="flex items-center justify-between gap-3">
        <h4 className="flex items-center gap-1.5 text-sm font-bold text-slate-700 dark:text-slate-200">
          <Dumbbell className="h-4 w-4" />练习掌握
        </h4>
        <MasteryBadge mastery={track.mastery} />
      </div>
      <dl className="mt-4 grid grid-cols-2 gap-2 text-xs">
        <PracticeFact label="最近辅助" value={ASSISTANCE_LABELS[result.assistance]} />
        <PracticeFact label="目标深度" value={result.targetDepth} />
        <PracticeFact label="未解决次数" value={`${track.details.statistics.unresolvedCount}`} />
        <PracticeFact label="能力迁移" value={TRANSFER_LABELS[track.details.transfer.status]} />
      </dl>
      <PatternList patterns={track.metadata.stablePatterns} />
    </section>
  );
}

function EmptyTrack({ title }: { title: string }) {
  return (
    <section className="rounded-xl border border-dashed border-slate-200 p-4 dark:border-slate-700">
      <h4 className="text-sm font-bold text-slate-600 dark:text-slate-300">{title}</h4>
      <p className="mt-3 text-sm text-slate-400">暂无记录</p>
    </section>
  );
}

function PracticeFact({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-white/80 p-2 dark:bg-slate-900/70">
      <dt className="text-slate-400">{label}</dt>
      <dd className="mt-1 font-semibold text-slate-700 dark:text-slate-200">{value}</dd>
    </div>
  );
}

function AbilityBadge({ ability }: { ability: EvaluatedAbility }) {
  const tone = ability === 'PROFICIENT'
    ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300'
    : ability === 'COMPETENT'
      ? 'bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300'
      : 'bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300';
  return <Badge label={getAbilityLabel(ability)} tone={tone} />;
}

function MasteryBadge({ mastery }: { mastery: PracticeMastery }) {
  const tone = mastery === 'INDEPENDENT'
    ? 'bg-emerald-100 text-emerald-700'
    : mastery === 'ASSISTED'
      ? 'bg-violet-100 text-violet-700'
      : 'bg-amber-100 text-amber-700';
  return <Badge label={MASTERY_LABELS[mastery]} tone={tone} />;
}

function Badge({ label, tone }: { label: string; tone: string }) {
  return <span className={`rounded-full px-2.5 py-1 text-xs font-bold ${tone}`}>{label}</span>;
}

function PatternList({ patterns }: { patterns: CandidateMemoryStablePattern[] }) {
  if (patterns.length === 0) return null;
  return (
    <div className="mt-4 border-t border-slate-200/70 pt-3 dark:border-slate-700">
      <p className="flex items-center gap-1.5 text-xs font-semibold text-slate-500">
        <Tags className="h-3.5 w-3.5" />稳定模式
      </p>
      <div className="mt-2 flex flex-wrap gap-1.5">
        {patterns.map(pattern => <PatternTag key={`${pattern.category}:${pattern.tag}`} pattern={pattern} />)}
      </div>
    </div>
  );
}

function PatternTag({ pattern }: { pattern: CandidateMemoryStablePattern }) {
  return (
    <span className="rounded-full border border-slate-200 px-2 py-1 text-[11px] text-slate-600 dark:border-slate-700 dark:text-slate-300">
      {CATEGORY_LABELS[pattern.category]} · {pattern.tag} × {pattern.episodeCount}
    </span>
  );
}
