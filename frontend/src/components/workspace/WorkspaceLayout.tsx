import { Moon, Sun } from 'lucide-react';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';
import { useTheme } from '../../hooks/useTheme';

const NAV_ITEMS = [
  { to: ROUTES.workspace, label: '新的面试', end: true },
  { to: ROUTES.workspaceHistory, label: '面试记录', end: false },
  { to: ROUTES.workspaceMemory, label: '候选人记忆', end: false },
  { to: ROUTES.providers, label: '模型服务', end: false },
] as const;

export default function WorkspaceLayout() {
  const { theme, toggleTheme } = useTheme();

  return (
    <div className="min-h-screen bg-paper text-ink">
      <header className="sticky top-0 z-40 border-b border-line bg-paper/90 backdrop-blur-sm">
        <div className="mx-auto flex h-[60px] max-w-[1280px] items-center justify-between px-5 sm:px-10">
          <Link to={ROUTES.workspace} className="flex items-baseline gap-2.5">
            <span className="h-2 w-2 translate-y-[-1px] rounded-[1px] bg-cinnabar" aria-hidden="true" />
            <span className="font-serifsc text-[17px] font-black tracking-wide">面试工作台</span>
          </Link>
          <div className="flex items-center gap-6">
            <nav className="hidden items-center gap-6 sm:flex" aria-label="主导航">
              {NAV_ITEMS.map(item => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    `border-b py-1 text-[13px] transition-colors duration-150 ${
                      isActive
                        ? 'border-cinnabar text-ink'
                        : 'border-transparent text-wk-muted hover:border-ink hover:text-ink'
                    }`
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
            <button
              type="button"
              onClick={toggleTheme}
              className="wk-btn-ghost flex h-8 w-8 items-center justify-center"
              aria-label={theme === 'dark' ? '切换为浅色模式' : '切换为深色模式'}
            >
              {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-[1280px] px-5 sm:px-10">
        <Outlet />
      </main>
    </div>
  );
}
