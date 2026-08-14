import request from './request';
import type {
  AdaptiveAssessmentReport,
  AdaptiveInterviewSession,
  CreateAdaptiveInterviewRequest,
  SubmitAdaptiveAnswerRequest,
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
};
