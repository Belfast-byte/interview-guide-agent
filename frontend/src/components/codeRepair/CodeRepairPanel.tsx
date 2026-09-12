import { useState } from 'react';
import type { CodeRepairTask } from '../../types/codeRepair';
import JavaEditor, { JavaDiff } from './JavaEditor';

export function CodeTaskBrief({ task }: { task: CodeRepairTask }) {
  return <div className="space-y-5 text-sm leading-7">
    <p className="text-xs text-wk-muted">Java 21 · 为面试构造的示例代码 · 模型代码审阅</p>
    <div><h3 className="wk-label">修复要求</h3>
      <ul className="mt-2 list-disc space-y-1 pl-5">{task.requirements.map(item => <li key={item}>{item}</li>)}</ul></div>
    <div><h3 className="wk-label">题目假设与接口约定</h3>
      <ul className="mt-2 list-disc space-y-1 pl-5 text-ink-soft">{task.assumptions.map(item => <li key={item}>{item}</li>)}</ul></div>
  </div>;
}

export default function CodeRepairPanel(props: {
  task: CodeRepairTask; code: string; currentLabel: string; readOnly?: boolean; onChange?: (code: string) => void;
}) {
  const [tab, setTab] = useState<'original' | 'current' | 'diff'>('current');
  const tabs = [{ id: 'original', label: '原始代码' }, { id: 'current', label: props.currentLabel },
    { id: 'diff', label: '差异' }] as const;
  return <section className="min-w-0 space-y-3" aria-label="Java 改错代码">
    <div className="flex flex-wrap gap-2 border-b border-line pb-2" role="tablist" aria-label="代码视图">
      {tabs.map(item => <button key={item.id} type="button" role="tab" aria-selected={tab === item.id}
        onClick={() => setTab(item.id)} className={`wk-btn-ghost ${tab === item.id ? 'text-cinnabar' : ''}`}>{item.label}</button>)}
    </div>
    {tab === 'original' && <JavaEditor code={props.task.initialCode} readOnly />}
    {tab === 'current' && <JavaEditor code={props.code} readOnly={props.readOnly} onChange={props.onChange} />}
    {tab === 'diff' && <JavaDiff original={props.task.initialCode} code={props.code} currentLabel={props.currentLabel} />}
  </section>;
}

export function OriginalTaskCode({ code }: { code: string }) {
  return <section className="space-y-3" aria-label="原始代码（题目材料）">
    <p className="wk-label">原始代码 · 题目材料</p>
    <p className="text-xs text-wk-muted">本轮未提交代码。</p>
    <JavaEditor code={code} readOnly />
  </section>;
}
