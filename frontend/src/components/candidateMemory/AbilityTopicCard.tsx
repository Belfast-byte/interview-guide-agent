import { BrainCircuit, Tags } from 'lucide-react';
import type {
  CandidateMemoryTagCount,
  CandidateMemoryTopic,
  MemoryTagCategory,
  SemanticAbility,
} from '../../types/candidateMemory';
import { getAbilityLabel } from './candidateMemoryView';

const LEVELS = ['L0', 'L1', 'L2', 'L3', 'L4'] as const;
const CATEGORY_LABELS: Record<MemoryTagCategory, string> = {
  ERROR_PATTERN: '错误模式',
  ANSWER_HABIT: '回答习惯',
};

export default function AbilityTopicCard({ topic }: { topic: CandidateMemoryTopic }) {
  const counts = [topic.l0Count, topic.l1Count, topic.l2Count, topic.l3Count, topic.l4Count];
  return (
    <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
            <BrainCircuit className="h-4 w-4" />{topic.skillId}
          </div>
          <h3 className="mt-1 text-lg font-bold text-slate-900 dark:text-white">{topic.focusId}</h3>
        </div>
        <AbilityBadge ability={topic.ability} />
      </div>

      <div className="mt-5 grid grid-cols-5 gap-2" aria-label="能力等级计数">
        {LEVELS.map((level, index) => (
          <div key={level} className="rounded-xl bg-slate-50 px-2 py-3 text-center dark:bg-slate-800">
            <p className="text-xs font-semibold text-slate-400">{level}</p>
            <p className="mt-1 text-lg font-bold text-slate-800 dark:text-slate-100">{counts[index]}</p>
          </div>
        ))}
      </div>

      <div className="mt-5 border-t border-slate-100 pt-4 dark:border-slate-800">
        <p className="flex items-center gap-2 text-xs font-semibold text-slate-500">
          <Tags className="h-4 w-4" />结构化标签
        </p>
        {topic.tagCounts.length === 0 ? (
          <p className="mt-3 text-sm text-slate-400">暂无标签统计</p>
        ) : (
          <div className="mt-3 flex flex-wrap gap-2">
            {topic.tagCounts.map(tag => <TagCount key={`${tag.category}:${tag.tag}`} tag={tag} />)}
          </div>
        )}
      </div>
    </article>
  );
}

function AbilityBadge({ ability }: { ability: SemanticAbility }) {
  const tone = ability === 'PROFICIENT'
    ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300'
    : ability === 'COMPETENT'
      ? 'bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-300'
      : 'bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-300';
  return <span className={`rounded-full px-3 py-1 text-xs font-bold ${tone}`}>{getAbilityLabel(ability)}</span>;
}

function TagCount({ tag }: { tag: CandidateMemoryTagCount }) {
  return (
    <span className="rounded-full border border-slate-200 px-2.5 py-1 text-xs text-slate-600 dark:border-slate-700 dark:text-slate-300">
      <span className="text-slate-400">{CATEGORY_LABELS[tag.category]}</span> · {tag.tag} × {tag.count}
    </span>
  );
}
