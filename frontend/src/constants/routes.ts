export const ROUTES = {
  home: '/',
  login: '/login',
  register: '/register',
  resumeUpload: '/upload',
  knowledgebaseUpload: '/knowledgebase/upload',
  adaptiveInterview: '/adaptive-interview',
  adaptiveInterviewSession: (sessionId: string) => `/adaptive-interview/${sessionId}`,
  agentInterview: '/agent-interview',
  agentInterviewSession: (sessionId: string) => `/agent-interview/${sessionId}`,
  agentModelSettings: '/agent-model-settings',
} as const;
