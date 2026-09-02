export const ROUTES = {
  home: '/',
  login: '/login',
  register: '/register',
  workspace: '/workspace',
  workspaceSession: (sessionId: string) => `/workspace/session/${sessionId}`,
  workspaceReport: (sessionId: string) => `/workspace/session/${sessionId}/report`,
  workspaceHistory: '/workspace/history',
  workspaceMemory: '/workspace/memory',
  providers: '/workspace/providers',
  architectureAudit: '/architecture-audit',
} as const;
