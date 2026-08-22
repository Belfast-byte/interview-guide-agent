import { ArrowRight, Bot, Sparkles } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ROUTES } from '../constants/routes';

export default function HomePage() {
  const { user } = useAuth();
  return (
    <div className="mx-auto max-w-6xl py-8">
      <section className="overflow-hidden rounded-3xl bg-gradient-to-br from-slate-950 via-indigo-950 to-primary-900 p-8 text-white shadow-2xl sm:p-12">
        <div className="max-w-3xl">
          <span className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold tracking-wide"><Sparkles className="h-3.5 w-3.5" />AI INTERVIEW AGENT</span>
          <h1 className="mt-6 text-3xl font-bold sm:text-5xl">你好，{user?.email}</h1>
          <p className="mt-4 max-w-2xl text-base leading-7 text-indigo-100 sm:text-lg">围绕岗位和项目经历规划能力维度，根据每轮回答动态调整追问。</p>
          <Link to={ROUTES.adaptiveInterview} className="mt-8 inline-flex items-center gap-2 rounded-xl bg-white px-5 py-3 font-semibold text-slate-950 transition hover:bg-indigo-50">开始自适应面试<ArrowRight className="h-4 w-4" /></Link>
        </div>
      </section>
      <section className="mt-8 grid gap-5">
        <HomeCard icon={Bot} title="进入自适应面试" description="输入职位描述和简历内容，由 Agent 规划考察维度、动态追问并生成证据化评估。" path={ROUTES.adaptiveInterview} action="进入面试" />
      </section>
    </div>
  );
}

function HomeCard(props: { icon: typeof Bot; title: string; description: string; path: string; action: string }) {
  return (
    <article className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <props.icon className="h-6 w-6 text-primary-600" />
      <h2 className="mt-4 text-xl font-bold text-slate-900 dark:text-white">{props.title}</h2>
      <p className="mt-2 min-h-12 text-sm leading-6 text-slate-500">{props.description}</p>
      <Link to={props.path} className="mt-5 inline-flex items-center gap-2 text-sm font-semibold text-primary-600">{props.action}<ArrowRight className="h-4 w-4" /></Link>
    </article>
  );
}
