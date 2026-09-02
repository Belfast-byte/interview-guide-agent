import { useEffect, useMemo, useState } from 'react';
import { AlertCircle, ArrowRight, Loader2, PenLine } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import { adaptiveInterviewApi } from '../../api/adaptiveInterview';
import { candidateProviderApi } from '../../api/candidateProvider';
import { getErrorMessage } from '../../api/request';
import { skillApi, type SkillDTO } from '../../api/skill';
import { ROUTES } from '../../constants/routes';
import type {
  AdaptiveSessionMode,
  CandidateLevel,
  CreateAdaptiveInterviewRequest,
} from '../../types/adaptiveInterview';
import type { CandidateProvider } from '../../types/candidateProvider';
import { AGENT_SAMPLE } from './sampleData';

/** 公司 persona 技能：选中公司即选中对应 skill */
const COMPANY_OPTIONS: readonly { value: string; label: string; skillId: string }[] = [
  { value: '', label: '不限', skillId: '' },
  { value: '字节跳动', label: '字节跳动', skillId: 'bytedance-backend' },
  { value: '阿里', label: '阿里', skillId: 'ali-backend' },
  { value: '腾讯', label: '腾讯', skillId: 'java-backend-tencent' },
];
const COMPANY_SKILL_IDS = COMPANY_OPTIONS.map(option => option.skillId).filter(Boolean);

const MODE_OPTIONS: readonly { value: AdaptiveSessionMode; label: string; desc: string }[] = [
  { value: 'EVALUATION', label: '评估模式', desc: '全面考察，产出正式评估报告' },
  { value: 'PRACTICE', label: '练习模式', desc: '对薄弱点进行练习深入' },
];

const LEVEL_OPTIONS: readonly { value: CandidateLevel; label: string }[] = [
  { value: 'INTERN', label: '实习' },
  { value: 'CAMPUS', label: '校招' },
  { value: 'EXPERIENCED', label: '社招' },
];

/** 由 skill 元数据拼一段可编辑的 JD 草稿 */
function composeJdDraft(skill: SkillDTO, companyLabel: string): string {
  const focuses = skill.categories.map((category, index) => `${index + 1}. ${category.label}`).join('\n');
  return `【${companyLabel}${skill.name}】

${skill.description}

考察重点：
${focuses}`;
}

export default function InterviewSetupPage() {
  const navigate = useNavigate();
  const [jd, setJd] = useState('');
  const [resume, setResume] = useState('');
  const [providerId, setProviderId] = useState('');
  const [mode, setMode] = useState<AdaptiveSessionMode>('EVALUATION');
  const [candidateLevel, setCandidateLevel] = useState<CandidateLevel>('CAMPUS');
  const [skills, setSkills] = useState<SkillDTO[]>([]);
  const [skillId, setSkillId] = useState('');
  const [focusId, setFocusId] = useState('');
  const [providers, setProviders] = useState<CandidateProvider[]>([]);
  const [providersLoading, setProvidersLoading] = useState(true);
  const [providerError, setProviderError] = useState('');
  const [working, setWorking] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setProvidersLoading(true);
    setProviderError('');
    Promise.all([candidateProviderApi.list(), skillApi.listSkills()])
      .then(([items, loadedSkills]) => {
        if (cancelled) return;
        setProviders(items);
        setProviderId(items.find(item => item.defaultChatProvider)?.id ?? '');
        setSkills(loadedSkills);
      })
      .catch(requestError => {
        if (!cancelled) setProviderError(getErrorMessage(requestError));
      })
      .finally(() => {
        if (!cancelled) setProvidersLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  /** 岗位下拉只列非公司 persona 的预设技能 */
  const roleSkills = useMemo(
    () => skills.filter(skill => skill.isPreset && !COMPANY_SKILL_IDS.includes(skill.id)),
    [skills],
  );
  const selectedSkill = skills.find(skill => skill.id === skillId);
  const companyLabel = COMPANY_OPTIONS.find(option => option.skillId === skillId)?.value ?? '';
  const hasDefaultProvider = providers.some(provider => provider.defaultChatProvider);
  const practiceReady = mode === 'EVALUATION' || Boolean(skillId && focusId);
  const ready = Boolean(jd.trim() && resume.trim() && providerId && hasDefaultProvider && practiceReady);
  const submitDisabled = working || providersLoading || !ready;

  const selectSkill = (nextSkillId: string) => {
    setSkillId(nextSkillId);
    setFocusId('');
    const skill = skills.find(item => item.id === nextSkillId);
    // JD 为空时按所选岗位带出一份可编辑草稿，已有内容不覆盖
    if (skill && !jd.trim()) {
      const company = COMPANY_OPTIONS.find(option => option.skillId === nextSkillId);
      setJd(composeJdDraft(skill, company ? `${company.label} · ` : ''));
    }
  };

  const createInterview = async () => {
    const request: CreateAdaptiveInterviewRequest = {
      jd: jd.trim(),
      resume: resume.trim(),
      ...(providerId ? { providerId } : {}),
      mode,
      candidateLevel,
      practiceScope: mode === 'PRACTICE' ? [{ skillId, focusId }] : [],
    };
    if (!request.jd || !request.resume) {
      setError('请填写职位描述和简历内容。');
      return;
    }
    if (mode === 'PRACTICE' && (!skillId || !focusId)) {
      setError('练习模式请选择目标岗位和考察重点。');
      return;
    }

    setWorking(true);
    setError('');
    await adaptiveInterviewApi.createStream(request, {
      onCreated: created => {
        navigate(ROUTES.workspaceSession(created.sessionId));
      },
      onDelta: () => {},
      onDone: () => {},
      onError: streamError => {
        setError(getErrorMessage(streamError));
      },
    });
    setWorking(false);
  };

  const selectedProvider = providers.find(provider => provider.id === providerId);
  const docketNo = `No. INT-${dayjs().format('YYYY-MM-DD')}`;

  return (
    <div className="pb-24">
      {/* ===== 页首 ===== */}
      <div className="wk-rise pt-10 sm:pt-16" style={{ animationDelay: '0.02s' }}>
        <p className="flex items-center gap-3 font-monosc text-[11.5px] tracking-[0.18em] text-cinnabar">
          <span className="h-px w-8 bg-cinnabar" aria-hidden="true" />
          ADAPTIVE INTERVIEW / 自适应面试
        </p>
        <h1 className="mt-5 max-w-[15em] font-serifsc text-[32px] font-black leading-[1.25] tracking-wide text-ink sm:text-[44px]">
          把下一场面试，<br />先在这里<span className="text-cinnabar">走完一遍</span>。
        </h1>
        <p className="mt-4 max-w-[44em] text-[15px] leading-7 text-wk-muted">
          选好岗位、贴好 JD 与简历，面试官会按你的回答实时调整追问的方向和深度。聊完给一份带证据的评估报告——哪里扎实、哪里露怯，都写在纸上。
        </p>
      </div>

      {(error || providerError) && (
        <div className="mt-6 space-y-2">
          {error && (
            <div className="wk-error">
              <AlertCircle className="mt-0.5 h-4 w-4 flex-none" />
              <span>{error}</span>
            </div>
          )}
          {providerError && (
            <div className="wk-error">
              <AlertCircle className="mt-0.5 h-4 w-4 flex-none" />
              <span>{providerError}</span>
            </div>
          )}
        </div>
      )}

      {/* ===== 主区：左表单 + 右面试单 ===== */}
      <div className="mt-10 grid items-start gap-10 lg:grid-cols-[7fr_5fr] xl:gap-16">
        <form
          onSubmit={event => {
            event.preventDefault();
            void createInterview();
          }}
        >
          {/* 01 面试目标 */}
          <section className="wk-rise border-t border-ink py-8" style={{ animationDelay: '0.16s' }}>
            <div className="mb-6 flex items-baseline gap-3.5">
              <span className="font-monosc text-xs tracking-wider text-cinnabar">01</span>
              <h2 className="font-serifsc text-lg font-bold text-ink">面试目标</h2>
              <span className="ml-auto text-xs text-wk-muted">决定要考什么</span>
            </div>
            <div className="grid gap-5 sm:grid-cols-2">
              <label className="block">
                <span className="wk-label">目标岗位{mode === 'PRACTICE' && ' *'}</span>
                <select
                  value={COMPANY_SKILL_IDS.includes(skillId) ? '' : skillId}
                  onChange={event => selectSkill(event.target.value)}
                  disabled={working}
                  className="wk-input mt-2"
                >
                  <option value="">请选择岗位方向</option>
                  {roleSkills.map(skill => (
                    <option key={skill.id} value={skill.id}>{skill.name}</option>
                  ))}
                </select>
              </label>
              <label className="block">
                <span className="wk-label">目标公司</span>
                <select
                  value={companyLabel}
                  onChange={event => selectSkill(COMPANY_OPTIONS.find(option => option.value === event.target.value)?.skillId ?? '')}
                  disabled={working}
                  className="wk-input mt-2"
                >
                  {COMPANY_OPTIONS.map(option => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </label>
            </div>
          </section>

          {/* 02 面试配置 */}
          <section className="wk-rise border-t border-line py-8" style={{ animationDelay: '0.22s' }}>
            <div className="mb-6 flex items-baseline gap-3.5">
              <span className="font-monosc text-xs tracking-wider text-cinnabar">02</span>
              <h2 className="font-serifsc text-lg font-bold text-ink">面试配置</h2>
              <span className="ml-auto text-xs text-wk-muted">决定怎么考</span>
            </div>
            <div className="space-y-5">
              <div>
                <span className="wk-label">面试模式 *</span>
                <div className="mt-2 flex flex-wrap gap-2">
                  {MODE_OPTIONS.map(option => (
                    <button
                      key={option.value}
                      type="button"
                      disabled={working}
                      data-active={mode === option.value}
                      onClick={() => setMode(option.value)}
                      className="wk-seg min-w-[160px] flex-1"
                    >
                      <span className="block text-[13.5px] font-bold">{option.label}</span>
                      <span className="wk-seg-desc mt-0.5 block text-[11.5px]">{option.desc}</span>
                    </button>
                  ))}
                </div>
              </div>

              {mode === 'PRACTICE' && (
                <label className="block">
                  <span className="wk-label">考察重点 *</span>
                  <select
                    value={focusId}
                    onChange={event => setFocusId(event.target.value)}
                    disabled={working || !selectedSkill}
                    className="wk-input mt-2"
                  >
                    <option value="">{selectedSkill ? '请选择考察重点' : '请先在上方选择目标岗位'}</option>
                    {selectedSkill?.categories.map(category => (
                      <option key={category.key} value={category.key}>{category.label}</option>
                    ))}
                  </select>
                </label>
              )}

              <div className="grid gap-5 sm:grid-cols-2">
                <div>
                  <span className="wk-label">候选人阶段 *</span>
                  <div className="mt-2 flex gap-2">
                    {LEVEL_OPTIONS.map(option => (
                      <button
                        key={option.value}
                        type="button"
                        disabled={working}
                        data-active={candidateLevel === option.value}
                        onClick={() => setCandidateLevel(option.value)}
                        className="wk-seg flex-1 text-center"
                      >
                        <span className="text-[13.5px] font-bold">{option.label}</span>
                      </button>
                    ))}
                  </div>
                </div>
                <label className="block">
                  <span className="wk-label">模型服务</span>
                  <select
                    value={providerId}
                    onChange={event => setProviderId(event.target.value)}
                    disabled={working || providersLoading || !hasDefaultProvider}
                    className="wk-input mt-2"
                  >
                    <option value="">{providersLoading ? '正在加载…' : '请选择模型服务'}</option>
                    {providers.map(provider => (
                      <option key={provider.id} value={provider.id}>
                        {provider.displayName} · {provider.model}{provider.defaultChatProvider ? '（默认）' : ''}
                      </option>
                    ))}
                  </select>
                  {!providersLoading && !hasDefaultProvider && (
                    <span className="mt-2 block text-xs text-cinnabar">
                      开始面试前必须设置默认模型服务。
                      <Link to={ROUTES.providers} className="ml-1 font-semibold underline">前往模型服务</Link>
                    </span>
                  )}
                </label>
              </div>
            </div>
          </section>

          {/* 03 职位描述 */}
          <section className="wk-rise border-t border-line py-8" style={{ animationDelay: '0.28s' }}>
            <div className="mb-6 flex items-baseline gap-3.5">
              <span className="font-monosc text-xs tracking-wider text-cinnabar">03</span>
              <h2 className="font-serifsc text-lg font-bold text-ink">职位描述</h2>
              <span className="ml-auto text-xs text-wk-muted">JD 越真，题越准</span>
            </div>
            <div>
              <div className="flex items-baseline justify-between">
                <span className="wk-label">JD 原文 *</span>
                <span className="flex items-center gap-3">
                  <span className="font-monosc text-[11px] text-wk-muted">{jd.length} 字</span>
                  <button
                    type="button"
                    onClick={() => setJd(AGENT_SAMPLE.jd)}
                    disabled={working}
                    className="wk-btn-ghost"
                  >
                    <PenLine className="h-3 w-3" />
                    填充 Agent 岗位示例
                  </button>
                </span>
              </div>
              <textarea
                value={jd}
                onChange={event => setJd(event.target.value)}
                rows={7}
                placeholder="粘贴目标岗位的职位描述。系统会从中提取考察维度，而不是出通用题。"
                className="wk-input mt-2 resize-y leading-7"
              />
            </div>
          </section>

          {/* 04 候选人简历 */}
          <section className="wk-rise border-t border-line py-8" style={{ animationDelay: '0.34s' }}>
            <div className="mb-6 flex items-baseline gap-3.5">
              <span className="font-monosc text-xs tracking-wider text-cinnabar">04</span>
              <h2 className="font-serifsc text-lg font-bold text-ink">候选人简历</h2>
              <span className="ml-auto text-xs text-wk-muted">追问会盯着你写过的东西</span>
            </div>
            <div>
              <div className="flex items-baseline justify-between">
                <span className="wk-label">简历要点 *</span>
                <span className="flex items-center gap-3">
                  <span className="font-monosc text-[11px] text-wk-muted">{resume.length} 字</span>
                  <button
                    type="button"
                    onClick={() => setResume(AGENT_SAMPLE.resume)}
                    disabled={working}
                    className="wk-btn-ghost"
                  >
                    <PenLine className="h-3 w-3" />
                    填充示例简历
                  </button>
                </span>
              </div>
              <textarea
                value={resume}
                onChange={event => setResume(event.target.value)}
                rows={9}
                placeholder="粘贴简历中的项目经历与技能描述。写在简历里的每一行，都可能被追问到底。"
                className="wk-input mt-2 resize-y leading-7"
              />
            </div>
          </section>

          {/* 提交行 */}
          <div className="wk-rise flex flex-col gap-4 border-t border-ink pt-7 sm:flex-row sm:items-center" style={{ animationDelay: '0.4s' }}>
            <button type="submit" disabled={submitDisabled} className="wk-cta">
              {working ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
              {working ? '正在规划并生成首题…' : '生成面试'}
              {!working && <ArrowRight className="h-4 w-4" />}
            </button>
            <p className="text-xs leading-5 text-wk-muted">
              {ready ? '材料齐了，随时可以开始。' : '填完 JD 与简历后即可开始。'}
            </p>
          </div>
        </form>

        {/* ===== 面试单 ===== */}
        <div className="wk-rise lg:sticky lg:top-20" style={{ animationDelay: '0.3s' }}>
          <aside className="wk-docket" aria-label="面试安排摘要">
            <div className="mb-2 flex items-start justify-between border-b border-ink pb-4">
              <div>
                <p className="font-serifsc text-xl font-black tracking-widest text-ink">面试单</p>
                <p className="mt-1 font-monosc text-[11px] tracking-wider text-wk-muted">{docketNo}</p>
              </div>
              <div className="wk-seal" data-ready={ready}>
                {ready ? '就绪' : '待开始'}
              </div>
            </div>
            <ul className="text-[13.5px]">
              <DocketRow label="岗位" value={selectedSkill?.name} empty="未选择" />
              <DocketRow label="公司" value={companyLabel} empty="不限" />
              <DocketRow
                label="模式"
                value={MODE_OPTIONS.find(option => option.value === mode)?.label
                  + (mode === 'PRACTICE' && focusId
                    ? ` · ${selectedSkill?.categories.find(category => category.key === focusId)?.label ?? ''}`
                    : '')}
              />
              <DocketRow label="阶段" value={LEVEL_OPTIONS.find(option => option.value === candidateLevel)?.label} />
              <DocketRow
                label="模型"
                value={providersLoading ? undefined : selectedProvider?.displayName}
                empty={providersLoading ? '加载中…' : '未配置'}
              />
              <DocketRow
                label="材料"
                value={jd.length + resume.length > 0 ? `JD ${jd.length} 字 · 简历 ${resume.length} 字` : undefined}
                empty="JD 0 字 · 简历 0 字"
              />
            </ul>
            <div className="mt-4 flex items-end justify-between border-t border-line pt-3.5">
              <div
                aria-hidden="true"
                className="h-[26px] w-[120px] opacity-75"
                style={{
                  background: `repeating-linear-gradient(90deg,
                    var(--ink) 0 2px, transparent 2px 5px,
                    var(--ink) 5px 6px, transparent 6px 11px,
                    var(--ink) 11px 14px, transparent 14px 17px)`,
                }}
              />
              <p className="text-right font-monosc text-[10px] leading-4 tracking-wider text-wk-muted">
                预计 25–40 分钟<br />约 6–8 轮问答
              </p>
            </div>
          </aside>
        </div>
      </div>
    </div>
  );
}

function DocketRow({ label, value, empty }: { label: string; value?: string; empty?: string }) {
  const blank = !value;
  return (
    <li className="flex items-baseline gap-3.5 border-b border-dashed border-line py-2.5 last:border-b-0">
      <span className="w-14 flex-none font-monosc text-[10.5px] uppercase tracking-[0.12em] text-wk-muted">{label}</span>
      <span className={`font-medium break-all ${blank ? 'font-normal text-wk-muted' : 'text-ink'}`}>
        {blank ? empty : value}
      </span>
    </li>
  );
}
