import { FormEvent, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { authApi } from '../api/auth';
import { useAuth } from '../auth/AuthContext';
import { getErrorMessage } from '../api/request';
import { ROUTES } from '../constants/routes';

const GREEN = '#2F6B4F';

export default function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { user, login } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [working, setWorking] = useState(false);
  const [error, setError] = useState('');

  if (user) {
    return <Navigate to={homePath(user.role)} replace />;
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setWorking(true);
    setError('');
    try {
      if (mode === 'register') {
        await authApi.register(email, password);
        navigate('/login', { replace: true, state: { registered: true } });
        return;
      }
      await login(email, password);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from ?? ROUTES.workspace, { replace: true });
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setWorking(false);
    }
  };

  return (
    <main className="flex min-h-screen items-center justify-center bg-paper px-4 py-12">
      <div className="wk-rise w-full max-w-[420px]">
        {/* ===== 品牌与页首 ===== */}
        <p className="flex items-baseline gap-2.5">
          <span className="h-2 w-2 translate-y-[-1px] rounded-[1px] bg-cinnabar" aria-hidden="true" />
          <span className="font-serifsc text-[17px] font-black tracking-wide text-ink">AI 面试平台</span>
        </p>
        <p className="mt-10 flex items-center gap-3 font-monosc text-[11.5px] tracking-[0.18em] text-cinnabar">
          <span className="h-px w-8 bg-cinnabar" aria-hidden="true" />
          {mode === 'login' ? 'SIGN IN / 登录' : 'SIGN UP / 注册'}
        </p>
        <h1 className="mt-4 font-serifsc text-[30px] font-black leading-[1.3] tracking-wide text-ink">
          {mode === 'login' ? '回到你的面试档案。' : '建立你的面试档案。'}
        </h1>

        {/* ===== 表单：档案单 ===== */}
        <form onSubmit={submit} className="wk-docket mt-8">
          {mode === 'login' && (location.state as { registered?: boolean } | null)?.registered && (
            <p
              className="mb-4 rounded-[3px] border px-3.5 py-2.5 text-[13px]"
              style={{
                borderColor: `color-mix(in srgb, ${GREEN} 35%, transparent)`,
                background: `color-mix(in srgb, ${GREEN} 8%, transparent)`,
                color: GREEN,
              }}
            >
              注册成功，请登录。
            </p>
          )}
          <AuthField label="邮箱" type="email" value={email} onChange={setEmail} autoComplete="email" />
          <AuthField label="密码" type="password" value={password} onChange={setPassword} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} />
          {error && <p role="alert" className="wk-error mt-4">{error}</p>}
          <button
            type="submit"
            disabled={working || !email.trim() || !password || (mode === 'register' && password.length < 8)}
            className="wk-cta mt-6 w-full"
          >
            {working && <Loader2 className="h-4 w-4 animate-spin" />}
            {working ? '处理中…' : mode === 'login' ? '登录' : '注册'}
          </button>
        </form>
        <p className="mt-5 text-center text-[13px] text-wk-muted">
          {mode === 'login' ? '还没有账号？' : '已有账号？'}
          <Link
            className="ml-1 font-semibold text-cinnabar underline underline-offset-4"
            to={mode === 'login' ? '/register' : '/login'}
          >
            {mode === 'login' ? '立即注册' : '返回登录'}
          </Link>
        </p>
      </div>
    </main>
  );
}

function AuthField(props: { label: string; type: string; value: string; onChange: (value: string) => void; autoComplete: string }) {
  const isNewPassword = props.autoComplete === 'new-password';
  return (
    <label className="mb-4 block">
      <span className="wk-label">{props.label}</span>
      <input
        required
        type={props.type}
        value={props.value}
        minLength={isNewPassword ? 8 : undefined}
        maxLength={props.type === 'password' ? 72 : 320}
        autoComplete={props.autoComplete}
        onChange={event => props.onChange(event.target.value)}
        className="wk-input mt-2"
      />
      {isNewPassword && <span className="mt-1.5 block text-[12px] text-wk-muted">至少 8 位。</span>}
    </label>
  );
}

function homePath(_role: string): string {
  return ROUTES.workspace;
}
