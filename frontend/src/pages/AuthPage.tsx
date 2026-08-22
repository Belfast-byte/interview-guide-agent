import { FormEvent, useState } from 'react';
import { BrainCircuit, Loader2, LockKeyhole, Mail } from 'lucide-react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { authApi } from '../api/auth';
import { useAuth } from '../auth/AuthContext';
import { getErrorMessage } from '../api/request';

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
      navigate(from ?? '/', { replace: true });
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setWorking(false);
    }
  };

  return (
    <main className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-50 to-indigo-100 px-4 dark:from-slate-950 dark:to-slate-900">
      <form onSubmit={submit} className="w-full max-w-md rounded-3xl border border-white/70 bg-white/90 p-8 shadow-2xl shadow-indigo-200/40 backdrop-blur dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-none">
        <div className="mb-7 flex items-center gap-3">
          <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-primary-600 text-white"><BrainCircuit className="h-5 w-5" /></span>
          <div><h1 className="text-xl font-bold text-slate-900 dark:text-white">{mode === 'login' ? '登录面试平台' : '注册候选人账号'}</h1><p className="text-sm text-slate-500">AI Interview Agent</p></div>
        </div>
        {mode === 'login' && (location.state as { registered?: boolean } | null)?.registered && <p className="mb-4 rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-700">注册成功，请登录。</p>}
        <AuthField icon={Mail} label="邮箱" type="email" value={email} onChange={setEmail} autoComplete="email" />
        <AuthField icon={LockKeyhole} label="密码" type="password" value={password} onChange={setPassword} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} />
        {error && <p role="alert" className="mt-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-300">{error}</p>}
        <button type="submit" disabled={working || !email.trim() || !password || (mode === 'register' && password.length < 8)} className="btn-primary mt-6 inline-flex w-full items-center justify-center gap-2 rounded-xl px-5 py-3 disabled:opacity-50">
          {working && <Loader2 className="h-4 w-4 animate-spin" />}{working ? '处理中' : mode === 'login' ? '登录' : '注册'}
        </button>
        <p className="mt-5 text-center text-sm text-slate-500">{mode === 'login' ? '还没有账号？' : '已有账号？'} <Link className="font-semibold text-primary-600" to={mode === 'login' ? '/register' : '/login'}>{mode === 'login' ? '立即注册' : '返回登录'}</Link></p>
      </form>
    </main>
  );
}

function AuthField(props: { icon: typeof Mail; label: string; type: string; value: string; onChange: (value: string) => void; autoComplete: string }) {
  const isNewPassword = props.autoComplete === 'new-password';
  return <label className="mb-4 block"><span className="mb-2 block text-sm font-semibold text-slate-700 dark:text-slate-200">{props.label}</span><span className="flex items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 dark:border-slate-700 dark:bg-slate-900"><props.icon className="h-4 w-4 text-slate-400" /><input required type={props.type} value={props.value} minLength={isNewPassword ? 8 : undefined} maxLength={props.type === 'password' ? 72 : 320} autoComplete={props.autoComplete} onChange={event => props.onChange(event.target.value)} className="w-full bg-transparent py-3 text-sm outline-none dark:text-white" /></span></label>;
}

function homePath(_role: string): string {
  return '/';
}
