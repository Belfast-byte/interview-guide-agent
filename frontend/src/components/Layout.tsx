import { Bot, History, Home, LogOut, Menu, Moon, ServerCog, Sparkles, Sun, X } from 'lucide-react';
import { useState } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ROUTES } from '../constants/routes';
import { useTheme } from '../hooks/useTheme';

const NAV_ITEMS = [
  { path: ROUTES.home, label: '首页', description: '开始候选人面试', icon: Home },
  {
    path: ROUTES.adaptiveInterview,
    label: '自适应面试',
    description: '按能力维度动态追问',
    icon: Bot,
  },
  {
    path: ROUTES.interviewHistory,
    label: '面试历史',
    description: '继续会话与查看报告',
    icon: History,
  },
  {
    path: ROUTES.providers,
    label: '模型服务',
    description: '管理私有 Provider',
    icon: ServerCog,
  },
] as const;

export default function Layout() {
  const location = useLocation();
  const { theme, toggleTheme } = useTheme();
  const { user, logout } = useAuth();
  const [navigationOpen, setNavigationOpen] = useState(false);

  return (
    <div className="flex min-h-screen bg-gradient-to-br from-slate-50 to-indigo-50 dark:from-slate-950 dark:to-slate-900">
      <button type="button" onClick={() => setNavigationOpen(true)} className="fixed left-4 top-4 z-40 flex h-11 w-11 items-center justify-center rounded-xl border border-slate-200 bg-white shadow-lg lg:hidden dark:border-slate-700 dark:bg-slate-900" aria-label="打开导航">
        <Menu className="h-5 w-5" />
      </button>
      {navigationOpen && <button type="button" onClick={() => setNavigationOpen(false)} className="fixed inset-0 z-40 bg-slate-950/45 lg:hidden" aria-label="关闭导航" />}

      <aside className={`${navigationOpen ? 'flex' : 'hidden'} fixed left-0 top-0 z-50 h-screen w-64 flex-col border-r border-slate-200 bg-white lg:flex dark:border-slate-800 dark:bg-slate-950`}>
        <div className="flex items-center justify-between border-b border-slate-100 p-6 dark:border-slate-800">
          <Link to="/" className="flex items-center gap-3" onClick={() => setNavigationOpen(false)}>
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary-600 text-white"><Sparkles className="h-5 w-5" /></span>
            <span><strong className="block text-slate-900 dark:text-white">AI Interview</strong><small className="text-slate-400">Agent 面试平台</small></span>
          </Link>
          <button type="button" onClick={() => setNavigationOpen(false)} className="lg:hidden" aria-label="关闭导航"><X className="h-5 w-5" /></button>
        </div>

        <nav className="flex-1 space-y-2 p-4">
          {NAV_ITEMS.map(item => {
            const active = item.path === '/' ? location.pathname === '/' : location.pathname.startsWith(item.path);
            return (
              <Link key={item.path} to={item.path} onClick={() => setNavigationOpen(false)} className={`flex items-center gap-3 rounded-xl px-3 py-3 ${active ? 'bg-primary-50 text-primary-700 dark:bg-primary-950/50 dark:text-primary-300' : 'text-slate-600 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-900'}`}>
                <item.icon className="h-5 w-5" />
                <span><strong className="block text-sm">{item.label}</strong><small className="text-xs text-slate-400">{item.description}</small></span>
              </Link>
            );
          })}
        </nav>

        <div className="space-y-3 border-t border-slate-100 p-4 dark:border-slate-800">
          <button type="button" onClick={toggleTheme} className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-900">
            {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}{theme === 'dark' ? '浅色模式' : '深色模式'}
          </button>
          <div className="rounded-xl bg-slate-50 px-3 py-3 dark:bg-slate-900">
            <p className="truncate text-xs text-slate-500">{user?.email}</p>
            <button type="button" onClick={logout} className="mt-2 flex items-center gap-1.5 text-xs text-slate-500 hover:text-red-600"><LogOut className="h-3.5 w-3.5" />退出登录</button>
          </div>
        </div>
      </aside>

      <main className="min-h-screen flex-1 p-4 pt-20 sm:p-8 sm:pt-20 lg:ml-64 lg:p-10"><Outlet /></main>
    </div>
  );
}
