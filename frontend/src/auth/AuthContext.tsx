import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { authApi } from '../api/auth';
import type { CurrentUser } from '../types/auth';
import {
  AUTH_CHANGED_EVENT,
  clearAccessToken,
  getAccessToken,
  storeAccessToken,
} from './token';

interface AuthContextValue {
  user: CurrentUser | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);

  const loadCurrentUser = useCallback(async () => {
    if (!getAccessToken()) {
      setUser(null);
      setLoading(false);
      return;
    }
    try {
      setUser(await authApi.me());
    } catch (error) {
      setUser(null);
      console.error('恢复登录状态失败', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadCurrentUser();
    const handleAuthChanged = () => setUser(null);
    window.addEventListener(AUTH_CHANGED_EVENT, handleAuthChanged);
    return () => window.removeEventListener(AUTH_CHANGED_EVENT, handleAuthChanged);
  }, [loadCurrentUser]);

  const login = useCallback(async (email: string, password: string) => {
    const token = await authApi.login(email, password);
    storeAccessToken(token.accessToken);
    try {
      setUser(await authApi.me());
    } catch (error) {
      clearAccessToken();
      throw error;
    }
  }, []);

  const logout = useCallback(() => {
    clearAccessToken();
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, loading, login, logout }),
    [user, loading, login, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth 必须在 AuthProvider 内使用');
  }
  return context;
}
