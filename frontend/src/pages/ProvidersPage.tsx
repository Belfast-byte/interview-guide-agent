import { AlertCircle, Loader2, Plus, RefreshCw, ServerCog } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { candidateProviderApi } from '../api/candidateProvider';
import { getErrorMessage } from '../api/request';
import CandidateProviderCard from '../components/CandidateProviderCard';
import CandidateProviderForm from '../components/CandidateProviderForm';
import ConfirmDialog from '../components/ConfirmDialog';
import type {
  CandidateProvider,
  CandidateProviderRequest,
  CandidateProviderTestResult,
} from '../types/candidateProvider';

export default function ProvidersPage() {
  const [providers, setProviders] = useState<CandidateProvider[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<CandidateProvider | null>(null);
  const [saving, setSaving] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<CandidateProvider | null>(null);
  const [testResults, setTestResults] = useState<Record<string, CandidateProviderTestResult>>({});

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setProviders(await candidateProviderApi.list());
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submit = async (request: CandidateProviderRequest) => {
    setSaving(true);
    setError('');
    try {
      if (editing) {
        await candidateProviderApi.update(editing.id, request);
      } else {
        await candidateProviderApi.create(request);
      }
      closeForm();
      await load();
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setSaving(false);
    }
  };

  const runAction = async (providerId: string, action: () => Promise<void>) => {
    setBusyId(providerId);
    setError('');
    try {
      await action();
      await load();
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setBusyId(null);
    }
  };

  const test = async (providerId: string) => {
    setBusyId(providerId);
    setError('');
    try {
      const result = await candidateProviderApi.test(providerId);
      setTestResults(current => ({ ...current, [providerId]: result }));
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setBusyId(null);
    }
  };

  const remove = async () => {
    if (!deleting) return;
    const target = deleting;
    await runAction(target.id, () => candidateProviderApi.delete(target.id));
    setDeleting(null);
  };

  const closeForm = () => {
    setFormOpen(false);
    setEditing(null);
  };

  const openEdit = (provider: CandidateProvider) => {
    setEditing(provider);
    setFormOpen(true);
  };

  return (
    <div className="mx-auto max-w-6xl pb-12">
      <header className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <div className="mb-3 flex items-center gap-2 text-primary-600 dark:text-primary-300"><ServerCog className="h-5 w-5" /><span className="text-xs font-bold uppercase tracking-wider">Private Providers</span></div>
          <h1 className="text-3xl font-bold text-slate-950 dark:text-white">我的模型服务</h1>
          <p className="mt-2 text-sm text-slate-500">管理自适应面试使用的文本模型；所有配置仅当前账号可见。</p>
        </div>
        <button type="button" onClick={() => { setEditing(null); setFormOpen(true); }} className="btn-primary inline-flex items-center justify-center gap-2 rounded-xl px-4 py-3"><Plus className="h-4 w-4" />新增 Provider</button>
      </header>

      <div className="mb-5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-900 dark:bg-amber-950/30 dark:text-amber-200">
        嵌入模型仅保存配置，暂未接入向量化、检索、题库或知识库业务链路。
      </div>

      {error && <div role="alert" className="mb-5 flex items-start gap-2 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300"><AlertCircle className="mt-0.5 h-4 w-4 flex-none" />{error}</div>}
      {formOpen && <CandidateProviderForm provider={editing} saving={saving} onCancel={closeForm} onSubmit={request => void submit(request)} />}

      {loading ? (
        <div className="flex min-h-64 items-center justify-center"><Loader2 className="h-8 w-8 animate-spin text-primary-500" /></div>
      ) : providers.length === 0 ? (
        <EmptyState onCreate={() => setFormOpen(true)} />
      ) : (
        <div className="grid gap-5 xl:grid-cols-2">
          {providers.map(provider => (
            <CandidateProviderCard
              key={provider.id}
              provider={provider}
              busy={busyId === provider.id}
              testResult={testResults[provider.id]}
              onEdit={() => openEdit(provider)}
              onTest={() => void test(provider.id)}
              onDelete={() => setDeleting(provider)}
              onDefaultChat={() => void runAction(provider.id, () => candidateProviderApi.setDefaultChat(provider.id))}
              onDefaultEmbedding={() => void runAction(provider.id, () => candidateProviderApi.setDefaultEmbedding(provider.id))}
            />
          ))}
        </div>
      )}

      {!loading && providers.length > 0 && <button type="button" onClick={() => void load()} className="mt-6 inline-flex items-center gap-2 text-sm text-slate-500 hover:text-primary-600"><RefreshCw className="h-4 w-4" />刷新配置</button>}
      <ConfirmDialog
        open={Boolean(deleting)}
        title="删除 Provider"
        message={deleting ? `确定删除“${deleting.displayName}”吗？默认或活动面试正在使用时，后端会拒绝删除。` : ''}
        confirmText="删除"
        confirmVariant="danger"
        loading={Boolean(deleting && busyId === deleting.id)}
        onConfirm={() => void remove()}
        onCancel={() => setDeleting(null)}
      />
    </div>
  );
}

function EmptyState({ onCreate }: { onCreate: () => void }) {
  return (
    <div className="rounded-2xl border border-dashed border-slate-300 bg-white/70 p-12 text-center dark:border-slate-700 dark:bg-slate-900/70">
      <ServerCog className="mx-auto h-10 w-10 text-slate-300" />
      <h2 className="mt-4 font-bold text-slate-800 dark:text-white">还没有模型服务</h2>
      <p className="mt-2 text-sm text-slate-500">先新增 Provider，并将其中一个设为默认文本模型后再开始面试。</p>
      <button type="button" onClick={onCreate} className="btn-primary mt-5 rounded-xl px-4 py-2.5">新增 Provider</button>
    </div>
  );
}
