export type AgentInterviewStatus = 'CREATED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';

export interface AgentInterviewTurn {
  turnNumber: number;
  question: string;
  answer: string | null;
}

export interface AgentInterviewSession {
  sessionId: string;
  runtimeVersion: 'agent-loop-mvp-v1' | 'agent-loop-v2';
  currentTurn: number;
  maxTurns: number;
  status: AgentInterviewStatus;
  selectedSkillId: string | null;
  selectedSkillHash: string | null;
  currentQuestion: string | null;
  finishReason: string | null;
  turns: AgentInterviewTurn[];
}

export interface CreateAgentInterviewRequest {
  jd: string;
  resume: string;
}

export interface SubmitAgentAnswerRequest {
  answer: string;
}

export interface CandidateAgentModelConfig {
  configured: boolean;
  baseUrl: string | null;
  maskedApiKey: string | null;
  model: string | null;
  temperature: number | null;
}

export interface SaveCandidateAgentModelConfigRequest {
  baseUrl: string;
  apiKey?: string;
  model: string;
  temperature: number;
}
