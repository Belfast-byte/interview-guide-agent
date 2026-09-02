import { AlertCircle, Plus, RefreshCw } from 'lucide-react';
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
    <div className="pb-24">
      {/* ===== 页首 ===== */}
      <div
        className="wk-rise flex flex-col gap-6 pt-10 sm:flex-row sm:items-end sm:justify-between"
        style={{ animationDelay: '0.02s' }}
      >
        <div>
          <p className="flex items-center gap-3 font-monosc text-[11.5px] tracking-[0.18em] text-cinnabar">
            <span className="h-px w-8 bg-cinnabar" aria-hidden="true" />
            MODEL PROVIDERS / 模型服务
          </p>
          <h1 className="mt-5 font-serifsc text-[32px] font-black leading-[1.25] tracking-wide text-ink sm:text-[40px]">
            用你自己的模型面试。
          </h1>
          <p className="mt-4 max-w-[44em] text-[15px] leading-7 text-wk-muted">
            管理自适应面试使用的文本模型；所有配置仅当前账号可见。
          </p>
        </div>
        <button
          type="button"
          onClick={() => { setEditing(null); setFormOpen(true); }}
          className="wk-cta flex-none"
        >
          <Plus className="h-4 w-4" />
          新增 Provider
        </button>
      </div>

      <p className="mt-8 rounded-[3px] border border-dashed border-line px-4 py-3 text-[13px] leading-6 text-wk-muted">
        嵌入模型仅保存配置，暂未接入向量化、检索、题库或知识库业务链路。
      </p>

      {error && (
        <div role="alert" className="wk-error mt-6">
          <AlertCircle className="mt-0.5 h-4 w-4 flex-none" />
          {error}
        </div>
      )}
      {formOpen && (
        <CandidateProviderForm
          provider={editing}
          saving={saving}
          onCancel={closeForm}
          onSubmit={request => void submit(request)}
        />
      )}

      {loading ? (
        <p className="mt-16 font-monosc text-xs tracking-[0.15em] text-wk-muted">载入中…</p>
      ) : providers.length === 0 ? (
        <EmptyState onCreate={() => setFormOpen(true)} />
      ) : (
        <div className="mt-10 grid gap-5 xl:grid-cols-2">
          {providers.map((provider, index) => (
            <CandidateProviderCard
              key={provider.id}
              provider={provider}
              index={index}
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

      {!loading && providers.length > 0 && (
        <button type="button" onClick={() => void load()} className="wk-btn-ghost mt-8">
          <RefreshCw className="h-3.5 w-3.5" />
          刷新配置
        </button>
      )}
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
    <div className="wk-rise mt-16 max-w-[36em]" style={{ animationDelay: '0.1s' }}>
      <p className="text-[15px] leading-7 text-wk-muted">
        还没有模型服务。先新增 Provider，并将其中一个设为默认文本模型后再开始面试。
      </p>
      <button type="button" onClick={onCreate} className="wk-cta mt-5">
        <Plus className="h-4 w-4" />
        新增 Provider
      </button>
    </div>
  );
}
