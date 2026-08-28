import { AlertCircle, BrainCircuit, Loader2, RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { adaptiveInterviewApi } from '../api/adaptiveInterview';
import { getErrorMessage } from '../api/request';
import AbilityTopicCard from '../components/candidateMemory/AbilityTopicCard';
import EpisodeMemoryList from '../components/candidateMemory/EpisodeMemoryList';
import type { CandidateMemoryResponse } from '../types/candidateMemory';

export default function CandidateMemoryPage() {
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
    <div className="mx-auto max-w-6xl pb-12">
      <header className="mb-7">
        <div className="mb-3 flex items-center gap-2 text-primary-600 dark:text-primary-300">
          <BrainCircuit className="h-5 w-5" />
          <span className="text-xs font-bold uppercase tracking-wider">Candidate Memory</span>
        </div>
        <h1 className="text-3xl font-bold text-slate-950 dark:text-white">候选人记忆</h1>
        <p className="mt-2 text-sm text-slate-500">分别查看正式能力、练习掌握与可追溯的问答经历。</p>
      </header>

      {error && <ErrorState message={error} retry={() => void load()} />}
      {loading ? (
        <div className="flex min-h-64 items-center justify-center">
          <Loader2 className="h-8 w-8 animate-spin text-primary-500" />
        </div>
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
    <div className="space-y-9">
      <section aria-labelledby="ability-topics-heading">
        <SectionHeading id="ability-topics-heading" title="双轨能力主题" detail={`${memory.topics.length} 个主题`} />
        {memory.topics.length === 0 ? (
          <EmptyState message="完成一场自适应面试后，这里会展示能力主题。" />
        ) : (
          <div className="grid gap-4 lg:grid-cols-2">
            {memory.topics.map(topic => (
              <AbilityTopicCard key={`${topic.skillId}:${topic.focusId}`} topic={topic} />
            ))}
          </div>
        )}
      </section>

      <section aria-labelledby="episode-memory-heading">
        <SectionHeading id="episode-memory-heading" title="问答追问链" detail={`${memory.episodes.totalElements} 条记忆`} />
        <EpisodeMemoryList episodes={memory.episodes} onPage={props.onPage} />
      </section>
    </div>
  );
}

function SectionHeading(props: { id: string; title: string; detail: string }) {
  return (
    <div className="mb-4 flex items-end justify-between gap-3">
      <h2 id={props.id} className="text-xl font-bold text-slate-900 dark:text-white">{props.title}</h2>
      <span className="text-xs text-slate-400">{props.detail}</span>
    </div>
  );
}

function ErrorState(props: { message: string; retry: () => void }) {
  return (
    <div role="alert" className="rounded-2xl border border-red-200 bg-red-50 p-5 text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300">
      <div className="flex items-center gap-2 font-semibold"><AlertCircle className="h-4 w-4" />候选人记忆加载失败</div>
      <p className="mt-2 text-sm">{props.message}</p>
      <button type="button" onClick={props.retry} className="mt-3 inline-flex items-center gap-1.5 text-sm font-semibold underline"><RefreshCw className="h-4 w-4" />重新加载</button>
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return <p className="rounded-2xl border border-dashed border-slate-300 bg-white/70 p-8 text-center text-sm text-slate-500 dark:border-slate-700 dark:bg-slate-900/70">{message}</p>;
}
