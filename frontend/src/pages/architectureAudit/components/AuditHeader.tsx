const STATS = [
  { value: '≈ 0', label: '控制面自主性', note: 'ASK / SWITCH / FINISH 全由 Java 决定' },
  { value: '0 / 3', label: '模型动态 Tool 调用', note: 'active Tool 全是伪 Tool；真 Tool 已下线' },
  { value: '~3,530', label: '恢复协议代码（行）', note: '占 adaptive 23,322 行生产代码约 15%' },
  { value: '5', label: 'P0 正确性问题', note: 'crash hole ×1、并发竞态 ×3、Prompt bug ×1' },
  { value: '5', label: '核心根因', note: '由 Top 10 问题合并而成' },
] as const;

const KEEP_LIST = ['Session / Turn 事实', '权限与 ownership', 'DB 唯一约束', '沙箱隔离与终态', '稳定业务幂等', '确定性最终报告'];
const DELETE_LIST = ['Generic ActionIntent', 'WorkState Patch', '伪 Tool 身份', '派生 Memory checkpoint', '死状态与参数袋'];

export default function AuditHeader() {
  return (
    <section id="verdict" className="scroll-mt-8">
      <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-zinc-500">
        架构审计 · 2026-08-29 · 已按生产代码逐条复核
      </div>
      <h1 className="mt-2 text-2xl font-semibold tracking-tight text-zinc-50 sm:text-3xl">
        Agent Runtime 复杂度审计
      </h1>

      <div className="mt-5 rounded-lg border border-zinc-800 bg-zinc-900/40 p-5">
        <p className="text-[15px] leading-relaxed text-zinc-200">
          当前系统是<span className="font-semibold text-zinc-50">「状态机驱动的 LLM 应用」</span>，
          不是完整 Agent Runtime：内容生成有模型参与，控制面几乎全部由 Java 预先裁决。
          系统为恢复<span className="text-zinc-50">可重算</span>的中间步骤引入了多套持久状态，
          却没有为真正的外部副作用和并发写入建立唯一 correctness owner。
        </p>
        <p className="mt-3 text-[13px] leading-relaxed text-zinc-400">
          三个最大问题：<span className="text-zinc-200">重复事实源</span>、
          <span className="text-zinc-200">伪 Agent 化</span>、
          <span className="text-zinc-200">Java 策略过度控制</span>。
          删除这些机制不会让系统变得不可靠，反而会减少事实漂移、恢复死角和并发失败面。
        </p>
      </div>

      <dl className="mt-4 grid grid-cols-2 gap-px overflow-hidden rounded-lg border border-zinc-800 bg-zinc-800 sm:grid-cols-5">
        {STATS.map(stat => (
          <div key={stat.label} className="bg-[#0c0c0e] p-4">
            <dd className="font-mono text-xl font-medium text-zinc-50">{stat.value}</dd>
            <dt className="mt-1 text-[12px] text-zinc-400">{stat.label}</dt>
            <dd className="mt-1 text-[11px] leading-snug text-zinc-600">{stat.note}</dd>
          </div>
        ))}
      </dl>

      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <div className="rounded-lg border border-emerald-400/20 bg-emerald-400/5 p-4">
          <div className="font-mono text-[10px] uppercase tracking-[0.2em] text-emerald-300/80">
            Keep · 业务必要复杂度
          </div>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {KEEP_LIST.map(item => (
              <span key={item} className="rounded border border-emerald-400/20 px-2 py-0.5 text-[11px] text-emerald-200/90">
                {item}
              </span>
            ))}
          </div>
        </div>
        <div className="rounded-lg border border-rose-400/20 bg-rose-400/5 p-4">
          <div className="font-mono text-[10px] uppercase tracking-[0.2em] text-rose-300/80">
            Delete · 偶然复杂度
          </div>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {DELETE_LIST.map(item => (
              <span key={item} className="rounded border border-rose-400/20 px-2 py-0.5 text-[11px] text-rose-200/90">
                {item}
              </span>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
