import { Check, Pencil, PlugZap, Star, Trash2 } from 'lucide-react';
import type {
  CandidateProvider,
  CandidateProviderTestResult,
} from '../types/candidateProvider';

interface CandidateProviderCardProps {
  provider: CandidateProvider;
  busy: boolean;
  testResult?: CandidateProviderTestResult;
  onEdit: () => void;
  onTest: () => void;
  onDelete: () => void;
  onDefaultChat: () => void;
  onDefaultEmbedding: () => void;
}

export default function CandidateProviderCard(props: CandidateProviderCardProps) {
  const provider = props.provider;
  return (
    <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="font-bold text-slate-950 dark:text-white">{provider.displayName}</h2>
          <p className="mt-1 break-all text-xs text-slate-500">{provider.baseUrl}</p>
        </div>
        <div className="flex flex-wrap justify-end gap-1">
          {provider.defaultChatProvider && <Badge label="默认文本" />}
          {provider.defaultEmbeddingProvider && <Badge label="默认嵌入" />}
        </div>
      </div>
      <dl className="mt-5 grid grid-cols-2 gap-3 text-sm">
        <Info label="文本模型" value={provider.model} />
        <Info label="API Key" value={provider.maskedApiKey} />
        <Info label="嵌入模型" value={provider.embeddingModel ?? '未配置'} />
        <Info label="嵌入维度" value={provider.embeddingDimensions?.toString() ?? '—'} />
        <Info label="Thinking" value={provider.thinkingDisabled ? '已关闭' : 'Provider 默认'} />
      </dl>
      {props.testResult && (
        <p className={`mt-4 rounded-lg px-3 py-2 text-xs ${props.testResult.success ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300' : 'bg-red-50 text-red-700 dark:bg-red-950/40 dark:text-red-300'}`}>
          {props.testResult.message}
        </p>
      )}
      <div className="mt-5 flex flex-wrap gap-2">
        <Action icon={Pencil} label="编辑" run={props.onEdit} disabled={props.busy} />
        <Action icon={PlugZap} label="测试" run={props.onTest} disabled={props.busy} />
        {!provider.defaultChatProvider && <Action icon={Star} label="设为默认文本" run={props.onDefaultChat} disabled={props.busy} />}
        {!provider.defaultEmbeddingProvider && provider.supportsEmbedding && <Action icon={Check} label="设为默认嵌入" run={props.onDefaultEmbedding} disabled={props.busy} />}
        <Action icon={Trash2} label="删除" run={props.onDelete} disabled={props.busy} danger />
      </div>
    </article>
  );
}

function Badge({ label }: { label: string }) {
  return <span className="rounded-full bg-primary-50 px-2 py-1 text-[11px] font-semibold text-primary-700 dark:bg-primary-950 dark:text-primary-300">{label}</span>;
}

function Info({ label, value }: { label: string; value: string }) {
  return <div className="min-w-0"><dt className="text-xs text-slate-400">{label}</dt><dd className="mt-1 truncate font-medium text-slate-700 dark:text-slate-200" title={value}>{value}</dd></div>;
}

function Action(props: { icon: typeof Pencil; label: string; run: () => void; disabled: boolean; danger?: boolean }) {
  return <button type="button" onClick={props.run} disabled={props.disabled} className={`inline-flex items-center gap-1.5 rounded-lg border px-3 py-2 text-xs font-semibold disabled:opacity-50 ${props.danger ? 'border-red-200 text-red-600 hover:bg-red-50 dark:border-red-900 dark:hover:bg-red-950' : 'border-slate-200 text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800'}`}><props.icon className="h-3.5 w-3.5" />{props.label}</button>;
}
