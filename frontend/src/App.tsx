import { lazy, Suspense } from 'react';
import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext';
import ProtectedRoute from './auth/ProtectedRoute';
import Layout from './components/Layout';
import AuthPage from './pages/AuthPage';
import { ROUTES } from './constants/routes';

const HomePage = lazy(() => import('./pages/HomePage'));
const AdaptiveInterviewPage = lazy(() => import('./pages/AdaptiveInterviewPage'));

function Loading() {
  return (
    <div className="flex min-h-[50vh] items-center justify-center">
      <div className="h-10 w-10 animate-spin rounded-full border-3 border-slate-200 border-t-primary-500" />
    </div>
  );
}

function CandidateRoute() {
  const { user } = useAuth();
  if (user?.role === 'CANDIDATE') {
    return <Outlet />;
  }
  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-50 px-4 dark:bg-slate-950">
      <div className="rounded-2xl bg-white p-8 text-center shadow-xl dark:bg-slate-900">
        <h1 className="text-xl font-bold text-slate-900 dark:text-white">当前账号不可使用候选人面试</h1>
        <p className="mt-2 text-sm text-slate-500">请使用候选人账号登录。</p>
      </div>
    </main>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Suspense fallback={<Loading />}>
          <Routes>
            <Route path="/login" element={<AuthPage mode="login" />} />
            <Route path="/register" element={<AuthPage mode="register" />} />
            <Route element={<ProtectedRoute />}>
              <Route element={<CandidateRoute />}>
                <Route path="/" element={<Layout />}>
                  <Route index element={<HomePage />} />
                  <Route path={ROUTES.adaptiveInterview} element={<AdaptiveInterviewPage />} />
                  <Route
                    path={`${ROUTES.adaptiveInterview}/:sessionId`}
                    element={<AdaptiveInterviewPage />}
                  />
                  <Route path="*" element={<Navigate to="/" replace />} />
                </Route>
              </Route>
            </Route>
          </Routes>
        </Suspense>
      </AuthProvider>
    </BrowserRouter>
  );
}
