export const ROUTES = {
  resumeUpload: '/upload',
  knowledgebaseUpload: '/knowledgebase/upload',
  adaptiveInterview: '/adaptive-interview',
  adaptiveInterviewSession: (sessionId: string) => `/adaptive-interview/${sessionId}`,
} as const;

