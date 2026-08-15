import request from './request';
import type {
  AdaptiveAssessmentReport,
  AdaptiveInterviewSession,
  CreateAdaptiveInterviewRequest,
  SubmitAdaptiveAnswerRequest,
  SubmitAlgorithmCodeRequest,
  SandboxExecution,
  ToolResultFollowUp,
} from '../types/adaptiveInterview';

const BASE_PATH = '/api/adaptive-agent-interviews';
const MODEL_CALL_TIMEOUT_MS = 45_000;

export const adaptiveInterviewApi = {
  create(payload: CreateAdaptiveInterviewRequest): Promise<AdaptiveInterviewSession> {
    return request.post<AdaptiveInterviewSession>(BASE_PATH, payload, {
      timeout: MODEL_CALL_TIMEOUT_MS,
    });
  },

  get(sessionId: string): Promise<AdaptiveInterviewSession> {
    return request.get<AdaptiveInterviewSession>(`${BASE_PATH}/${sessionId}`);
  },

  submitAnswer(
    sessionId: string,
    payload: SubmitAdaptiveAnswerRequest,
  ): Promise<AdaptiveInterviewSession> {
    return request.post<AdaptiveInterviewSession>(
      `${BASE_PATH}/${sessionId}/answers`,
      payload,
      { timeout: MODEL_CALL_TIMEOUT_MS },
    );
  },

  getReport(sessionId: string): Promise<AdaptiveAssessmentReport> {
    return request.get<AdaptiveAssessmentReport>(`${BASE_PATH}/${sessionId}/report`);
  },

  submitCode(
    sessionId: string,
    payload: SubmitAlgorithmCodeRequest,
  ): Promise<SandboxExecution> {
    return request.post<SandboxExecution>(
      `${BASE_PATH}/${sessionId}/algorithm/submissions`,
      payload,
    );
  },

  getCodeSubmission(sessionId: string, submissionId: string): Promise<SandboxExecution> {
    return request.get<SandboxExecution>(
      `${BASE_PATH}/${sessionId}/algorithm/submissions/${submissionId}`,
    );
  },

  getLatestCodeSubmission(sessionId: string, turnIndex: number): Promise<SandboxExecution> {
    return request.get<SandboxExecution>(
      `${BASE_PATH}/${sessionId}/algorithm/submissions/latest`,
      { params: { turnIndex } },
    );
  },

  getToolResultFollowUps(sessionId: string): Promise<ToolResultFollowUp[]> {
    return request.get<ToolResultFollowUp[]>(
      `${BASE_PATH}/${sessionId}/tool-result-follow-ups`,
    );
  },
};
