import { Loader2, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import type {
  CandidateProvider,
  CandidateProviderRequest,
} from '../types/candidateProvider';

interface CandidateProviderFormProps {
  provider: CandidateProvider | null;
  saving: boolean;
  onCancel: () => void;
  onSubmit: (request: CandidateProviderRequest) => void;
}

interface FormState {
  displayName: string;
  baseUrl: string;
  apiKey: string;
  model: string;
  embeddingModel: string;
  embeddingDimensions: string;
  temperature: string;
  thinkingDisabled: boolean;
}

const EMPTY_FORM: FormState = {
  displayName: '',
  baseUrl: '',
  apiKey: '',
  model: '',
  embeddingModel: '',
  embeddingDimensions: '',
  temperature: '',
  thinkingDisabled: false,
};

export default function CandidateProviderForm(props: CandidateProviderFormProps) {
  const [form, setForm] = useState<FormState>(EMPTY_FORM);

  useEffect(() => {
    const provider = props.provider;
    setForm(provider ? {
      displayName: provider.displayName,
      baseUrl: provider.baseUrl,
      apiKey: '',
      model: provider.model,
      embeddingModel: provider.embeddingModel ?? '',
      embeddingDimensions: provider.embeddingDimensions?.toString() ?? '',
      temperature: provider.temperature?.toString() ?? '',
      thinkingDisabled: provider.thinkingDisabled,
    } : EMPTY_FORM);
  }, [props.provider]);

  const update = <K extends keyof FormState,>(field: K, value: FormState[K]) => {
    setForm(current => ({ ...current, [field]: value }));
  };

  const submit = () => {
    const embeddingModel = form.embeddingModel.trim();
    props.onSubmit({
      displayName: form.displayName.trim(),
      baseUrl: form.baseUrl.trim(),
      apiKey: form.apiKey.trim(),
      model: form.model.trim(),
      ...(embeddingModel ? { embeddingModel } : {}),
      ...(embeddingModel && form.embeddingDimensions
        ? { embeddingDimensions: Number(form.embeddingDimensions) }
        : {}),
      ...(form.temperature ? { temperature: Number(form.temperature) } : {}),
      thinkingDisabled: form.thinkingDisabled,
    });
  };

  const valid = form.displayName.trim() && form.baseUrl.trim() && form.model.trim()
    && (props.provider || form.apiKey.trim());

  return (
    <section className="mb-6 rounded-2xl border border-primary-200 bg-white p-6 shadow-lg dark:border-primary-900 dark:bg-slate-900">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="text-lg font-bold text-slate-950 dark:text-white">
            {props.provider ? '编辑 Provider' : '新增 Provider'}
          </h2>
          <p className="mt-1 text-xs text-slate-500">
            {props.provider ? 'API Key 留空会保留原密钥。' : 'Provider 仅当前候选人可见。'}
          </p>
        </div>
        <button type="button" onClick={props.onCancel} aria-label="关闭表单"><X /></button>
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        <Field label="名称" value={form.displayName} onChange={value => update('displayName', value)} />
        <Field label="服务地址" value={form.baseUrl} onChange={value => update('baseUrl', value)} placeholder="https://api.example.com/v1" />
        <Field label="文本模型" value={form.model} onChange={value => update('model', value)} />
        <Field label={props.provider ? 'API Key（留空保留）' : 'API Key'} value={form.apiKey} onChange={value => update('apiKey', value)} password />
        <Field label="嵌入模型（可选）" value={form.embeddingModel} onChange={value => update('embeddingModel', value)} />
        <Field label="嵌入维度（可选）" value={form.embeddingDimensions} onChange={value => update('embeddingDimensions', value)} number />
        <Field label="温度（可选）" value={form.temperature} onChange={value => update('temperature', value)} number />
        <label className="flex items-center gap-3 text-sm font-medium text-slate-700 dark:text-slate-200">
          <input
            type="checkbox"
            checked={form.thinkingDisabled}
            onChange={event => update('thinkingDisabled', event.target.checked)}
            className="h-4 w-4 rounded border-slate-300 text-primary-600"
          />
          关闭 Thinking
        </label>
      </div>
      <p className="mt-4 text-xs text-amber-700 dark:text-amber-300">
        嵌入模型仅保存配置，暂未接入业务链路；连接测试只验证文本模型。
      </p>
      <div className="mt-5 flex justify-end gap-3">
        <button type="button" onClick={props.onCancel} className="btn-secondary rounded-xl px-4 py-2">取消</button>
        <button type="button" onClick={submit} disabled={!valid || props.saving} className="btn-primary inline-flex items-center gap-2 rounded-xl px-4 py-2 disabled:opacity-50">
          {props.saving && <Loader2 className="h-4 w-4 animate-spin" />}
          保存
        </button>
      </div>
    </section>
  );
}

interface FieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  password?: boolean;
  number?: boolean;
}

function Field(props: FieldProps) {
  return (
    <label className="text-sm font-medium text-slate-700 dark:text-slate-200">
      {props.label}
      <input
        type={props.password ? 'password' : props.number ? 'number' : 'text'}
        value={props.value}
        onChange={event => props.onChange(event.target.value)}
        placeholder={props.placeholder}
        step={props.number ? 'any' : undefined}
        className="dark-input mt-2 w-full rounded-xl px-3 py-2.5"
      />
    </label>
  );
}
