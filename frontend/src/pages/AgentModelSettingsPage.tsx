import { FormEvent, useEffect, useState } from 'react';
import { KeyRound, Loader2, Save } from 'lucide-react';
import { agentInterviewApi } from '../api/agentInterview';
import { getErrorMessage } from '../api/request';

interface ModelConfigForm {
  baseUrl: string;
  apiKey: string;
  model: string;
  temperature: number;
}

const EMPTY_FORM: ModelConfigForm = {
  baseUrl: '', apiKey: '', model: '', temperature: 0.2,
};
const INPUT_CLASSES = 'w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-900 outline-none transition focus:border-primary-500 focus:ring-2 focus:ring-primary-500/20 dark:border-slate-600 dark:bg-slate-900 dark:text-white';

export default function AgentModelSettingsPage() {
  const { form, setForm, loading, saving, message, submit } = useModelConfigForm();
  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <header><p className="text-sm font-semibold text-primary-600">Agent 模型</p><h1 className="mt-1 text-3xl font-bold text-slate-900 dark:text-white">配置你的模型</h1><p className="mt-2 text-sm text-slate-500">配置仅属于当前候选人，API Key 加密保存。支持 OpenAI 兼容接口。</p></header>
      <form onSubmit={submit} className="space-y-5 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800">
        <Field label="模型服务地址"><input required type="url" value={form.baseUrl} onChange={event => setForm({ ...form, baseUrl: event.target.value })} placeholder="https://api.openai.com" className={INPUT_CLASSES} /></Field>
        <Field label="API Key"><input type="password" value={form.apiKey} onChange={event => setForm({ ...form, apiKey: event.target.value })} placeholder="首次配置必填；留空则保留原 Key" autoComplete="off" className={INPUT_CLASSES} /></Field>
        <Field label="模型名称"><input required value={form.model} onChange={event => setForm({ ...form, model: event.target.value })} placeholder="gpt-4.1-mini" className={INPUT_CLASSES} /></Field>
        <Field label="Temperature"><input required type="number" min="0" max="2" step="0.1" value={form.temperature} onChange={event => setForm({ ...form, temperature: Number(event.target.value) })} className={INPUT_CLASSES} /></Field>
        {message && <p role="status" className="rounded-xl bg-slate-50 px-4 py-3 text-sm text-slate-600 dark:bg-slate-900 dark:text-slate-300">{message}</p>}
        <button disabled={loading || saving} className="btn-primary inline-flex items-center gap-2 rounded-xl px-5 py-3 disabled:opacity-50">{saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}{saving ? '保存中' : '保存配置'}</button>
      </form>
    </div>
  );
}

function useModelConfigForm() {
  const [form, setForm] = useState<ModelConfigForm>(EMPTY_FORM);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  useEffect(() => {
    agentInterviewApi.getModelConfig().then(config => {
      if (config.configured) setForm({ baseUrl: config.baseUrl ?? '', apiKey: '', model: config.model ?? '', temperature: config.temperature ?? 0.2 });
    }).catch(error => setMessage(getErrorMessage(error))).finally(() => setLoading(false));
  }, []);
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setMessage('');
    try {
      const saved = await agentInterviewApi.saveModelConfig(form);
      setForm(current => ({ ...current, apiKey: '' }));
      setMessage(`配置已保存（${saved.maskedApiKey}）`);
    } catch (error) { setMessage(getErrorMessage(error)); }
    finally { setSaving(false); }
  };
  return { form, setForm, loading, saving, message, submit };
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="block"><span className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200"><KeyRound className="h-4 w-4 text-primary-500" />{label}</span>{children}</label>;
}
