export const ROUTES = {
  home: '/',
  login: '/login',
  register: '/register',
  resumeUpload: '/upload',
  knowledgebaseUpload: '/knowledgebase/upload',
  adaptiveInterview: '/adaptive-interview',
  adaptiveInterviewSession: (sessionId: string) => `/adaptive-interview/${sessionId}`,
  candidateMemory: '/candidate-memory',
  providers: '/providers',
  interviewHistory: '/interview-history',
} as const;
