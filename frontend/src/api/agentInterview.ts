import { request } from './request';
import type {
  AgentInterviewSession,
  CandidateAgentModelConfig,
  CreateAgentInterviewRequest,
  SaveCandidateAgentModelConfigRequest,
  SubmitAgentAnswerRequest,
} from '../types/agentInterview';

const AGENT_LOOP_TIMEOUT_MS = 45_000;

export const agentInterviewApi = {
  getModelConfig(): Promise<CandidateAgentModelConfig> {
    return request.get<CandidateAgentModelConfig>('/api/interview/agent-loop/model-config');
  },

  saveModelConfig(
    payload: SaveCandidateAgentModelConfigRequest,
  ): Promise<CandidateAgentModelConfig> {
    return request.put<CandidateAgentModelConfig>(
      '/api/interview/agent-loop/model-config',
      payload,
    );
  },

  createSession(payload: CreateAgentInterviewRequest): Promise<AgentInterviewSession> {
    return request.post<AgentInterviewSession>(
      '/api/interview/agent-loop/sessions',
      payload,
      { timeout: AGENT_LOOP_TIMEOUT_MS },
    );
  },

  getSession(sessionId: string): Promise<AgentInterviewSession> {
    return request.get<AgentInterviewSession>(
      `/api/interview/agent-loop/sessions/${sessionId}`,
    );
  },

  submitAnswer(
    sessionId: string,
    payload: SubmitAgentAnswerRequest,
  ): Promise<AgentInterviewSession> {
    return request.post<AgentInterviewSession>(
      `/api/interview/agent-loop/sessions/${sessionId}/answers`,
      payload,
      { timeout: AGENT_LOOP_TIMEOUT_MS },
    );
  },
};
