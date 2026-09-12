import { AlertCircle } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { adaptiveInterviewApi } from '../../api/adaptiveInterview';
import { getErrorMessage } from '../../api/request';
import EpisodeMemoryList, { EpisodeMemoryDetail } from '../../components/candidateMemory/EpisodeMemoryList';
import type { CandidateMemoryResponse, CandidateMemorySkill } from '../../types/candidateMemory';

function useMemoryPage() {
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
  useEffect(() => { void load(); }, [load]);
  return { memory, loading, error, load, setPage };
}

export default function WorkspaceMemoryPage() {
  const { memory, loading, error, load, setPage } = useMemoryPage();
  return (
    <div className="pb-24">
      <div className="wk-rise pt-10">
        <p className="font-monosc text-xs tracking-widest text-cinnabar">CANDIDATE MEMORY / 候选人记忆</p>
        <h1 className="mt-5 font-serifsc text-4xl font-black text-ink">面试官记得你说过什么。</h1>
        <p className="mt-4 max-w-2xl text-sm leading-7 text-wk-muted">
          按职位查看最近的知识点表现。每次回答保留原场景，练习时参考这些经历，换个场景验证不足。
        </p>
      </div>
      {error && (
        <div className="wk-error mt-8">
          <AlertCircle className="h-4 w-4 flex-none" />
          <span>候选人记忆加载失败：{error}</span>
          <button type="button" onClick={() => void load()} className="ml-auto underline">重新加载</button>
        </div>
      )}
      {loading ? <p className="mt-12 text-sm text-wk-muted">载入中…</p> : !error && memory && (
        <MemoryContent memory={memory} onPage={setPage} />
      )}
    </div>
  );
}

function MemoryContent({ memory, onPage }: {
  memory: CandidateMemoryResponse;
  onPage: (page: number) => void;
}) {
  return (
    <>
      <section className="mt-12 space-y-6" aria-labelledby="skill-memory-heading">
        <h2 id="skill-memory-heading" className="font-serifsc text-2xl font-bold text-ink">职位画像</h2>
        <p className="text-sm text-wk-muted">这里展示最近一次正式评估；一次表现不代表永久掌握，历史不足也不自动成为本场任务。</p>
        {memory.skills.length === 0 && <p className="text-sm text-wk-muted">还没有已考察的职位。</p>}
        {memory.skills.map(skill => <SkillProfile key={skill.skillId} skill={skill} />)}
      </section>
      <section className="mt-14" aria-labelledby="episode-memory-heading">
        <h2 id="episode-memory-heading" className="mb-5 font-serifsc text-2xl font-bold text-ink">问答经历</h2>
        <EpisodeMemoryList episodes={memory.episodes} onPage={onPage} />
      </section>
    </>
  );
}

function SkillProfile({ skill }: { skill: CandidateMemorySkill }) {
  return (
    <article className="rounded-lg border border-line p-5">
      <h3 className="mb-4 text-lg font-bold text-ink">{skill.skillName}</h3>
      <div className="space-y-3">
        {skill.topics.map(topic => (
          <details key={topic.focusId} className="border-t border-line pt-3">
            <summary className="cursor-pointer text-sm text-ink">
              {topic.focusName} · {topic.latest ? `最近表现 ${topic.latest.depthLevel}` : '未考察'}
            </summary>
            <div className="mt-3">
              {topic.latest ? <EpisodeMemoryDetail episode={topic.latest} />
                : <p className="text-sm text-wk-muted">暂无正式评估。</p>}
            </div>
          </details>
        ))}
      </div>
    </article>
  );
}
