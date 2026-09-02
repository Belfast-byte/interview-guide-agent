import { Check, Pencil, PlugZap, Star, Trash2 } from 'lucide-react';
import type {
  CandidateProvider,
  CandidateProviderTestResult,
} from '../types/candidateProvider';

const GREEN = '#2F6B4F';
const TONES = {
  cinnabar: { background: 'color-mix(in srgb, var(--cinnabar) 10%, transparent)', color: 'var(--cinnabar)' },
  ink: { background: 'color-mix(in srgb, var(--ink) 7%, transparent)', color: 'var(--ink-soft)' },
  green: { background: `color-mix(in srgb, ${GREEN} 12%, transparent)`, color: GREEN },
} as const;

interface CandidateProviderCardProps {
  provider: CandidateProvider;
  index: number;
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
    <article
      className="wk-rise rounded border border-line bg-raised px-6 py-5"
      style={{ animationDelay: `${0.08 + props.index * 0.04}s` }}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <h2 className="font-serifsc text-lg font-bold tracking-wide text-ink">
            {provider.displayName}
          </h2>
          <p className="mt-1.5 break-all font-monosc text-[11.5px] text-wk-muted">
            {provider.baseUrl}
          </p>
        </div>
        <div className="flex flex-none flex-wrap justify-end gap-1.5">
          {provider.defaultChatProvider && <span className="wk-tag" style={TONES.cinnabar}>默认文本</span>}
          {provider.defaultEmbeddingProvider && <span className="wk-tag" style={TONES.ink}>默认嵌入</span>}
        </div>
      </div>
      <dl className="mt-5 grid grid-cols-2 gap-x-4 gap-y-4 border-t border-dashed border-line pt-4 sm:grid-cols-3">
        <Info label="文本模型" value={provider.model} mono />
        <Info label="API Key" value={provider.maskedApiKey} mono />
        <Info label="嵌入模型" value={provider.embeddingModel ?? '未配置'} mono />
        <Info label="嵌入维度" value={provider.embeddingDimensions?.toString() ?? '—'} mono />
        <Info label="Thinking" value={provider.thinkingDisabled ? '已关闭' : 'Provider 默认'} />
      </dl>
      {props.testResult && (
        <p
          className={props.testResult.success ? 'mt-4 rounded-[3px] border px-3.5 py-2.5 text-[13px]' : 'wk-error mt-4'}
          style={props.testResult.success
            ? {
                borderColor: `color-mix(in srgb, ${GREEN} 35%, transparent)`,
                background: TONES.green.background,
                color: GREEN,
              }
            : undefined}
        >
          {props.testResult.message}
        </p>
      )}
      <div className="mt-5 flex flex-wrap gap-2 border-t border-dashed border-line pt-4">
        <Action icon={Pencil} label="编辑" run={props.onEdit} disabled={props.busy} />
        <Action icon={PlugZap} label="测试" run={props.onTest} disabled={props.busy} />
        {!provider.defaultChatProvider && (
          <Action icon={Star} label="设为默认文本" run={props.onDefaultChat} disabled={props.busy} />
        )}
        {!provider.defaultEmbeddingProvider && provider.supportsEmbedding && (
          <Action icon={Check} label="设为默认嵌入" run={props.onDefaultEmbedding} disabled={props.busy} />
        )}
        <Action icon={Trash2} label="删除" run={props.onDelete} disabled={props.busy} danger />
      </div>
    </article>
  );
}

function Info({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="min-w-0">
      <dt className="wk-label">{label}</dt>
      <dd
        className={`mt-1 truncate text-ink ${mono ? 'font-monosc text-[12px]' : 'text-[13px]'}`}
        title={value}
      >
        {value}
      </dd>
    </div>
  );
}

function Action(props: { icon: typeof Pencil; label: string; run: () => void; disabled: boolean; danger?: boolean }) {
  return (
    <button
      type="button"
      onClick={props.run}
      disabled={props.disabled}
      className="wk-btn-ghost"
      style={props.danger ? { color: 'var(--cinnabar)' } : undefined}
    >
      <props.icon className="h-3.5 w-3.5" />
      {props.label}
    </button>
  );
}
