import request from './request';
import { streamSse } from './stream';
import { getAccessToken } from '../auth/token';
import type {
  AdaptiveAssessmentReport,
  AdaptiveInterviewHistoryPage,
  AdaptiveInterviewSession,
  CreateAdaptiveInterviewRequest,
  SubmitAdaptiveAnswerRequest,
  SubmitAlgorithmCodeRequest,
  SandboxExecution,
  PublicAlgorithmProblem,
} from '../types/adaptiveInterview';
import type { CandidateMemoryResponse } from '../types/candidateMemory';

const BASE_PATH = '/api/adaptive-agent-interviews';
const MODEL_CALL_TIMEOUT_MS = 45_000;
const ANSWER_STREAM_TIMEOUT_MS = 75_000;
const CREATION_STREAM_TIMEOUT_MS = 130_000;

export type AnswerStreamStage = 'assessing' | 'generating';

export interface SubmitAnswerStreamCallbacks {
  onStage: (stage: AnswerStreamStage) => void;
  onDelta: (delta: string) => void;
  onDone: (session: AdaptiveInterviewSession) => void;
  onError: (error: Error) => void;
}

export interface CreateInterviewStreamCallbacks {
  onCreated: (session: AdaptiveInterviewSession) => void;
  onDelta: (delta: string) => void;
  onDone: (session: AdaptiveInterviewSession) => void;
  onError: (error: Error) => void;
}

export const adaptiveInterviewApi = {
  create(payload: CreateAdaptiveInterviewRequest): Promise<AdaptiveInterviewSession> {
    return request.post<AdaptiveInterviewSession>(BASE_PATH, payload);
  },

  createStream(
    payload: CreateAdaptiveInterviewRequest,
    callbacks: CreateInterviewStreamCallbacks,
  ): Promise<void> {
    return consumeInterviewStream({
      url: `${BASE_PATH}/stream`,
      payload,
      timeoutMillis: CREATION_STREAM_TIMEOUT_MS,
      callbacks: {
        onCreated: callbacks.onCreated,
        onDelta: callbacks.onDelta,
        onDone: callbacks.onDone,
        onError: callbacks.onError,
      },
    });
  },

  get(sessionId: string): Promise<AdaptiveInterviewSession> {
    return request.get<AdaptiveInterviewSession>(`${BASE_PATH}/${sessionId}`);
  },

  history(page: number): Promise<AdaptiveInterviewHistoryPage> {
    return request.get<AdaptiveInterviewHistoryPage>(`${BASE_PATH}/history`, {
      params: { page },
    });
  },

  getCandidateMemory(page: number): Promise<CandidateMemoryResponse> {
    return request.get<CandidateMemoryResponse>(`${BASE_PATH}/me/memory`, {
      params: { page },
    });
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

  /**
   * 流式提交文本回答：SSE 推送阶段事件与题目增量文本，完成时回调权威会话。
   * 代码提交回答请使用同步的 submitAnswer。
   */
  submitAnswerStream(
    sessionId: string,
    payload: SubmitAdaptiveAnswerRequest,
    callbacks: SubmitAnswerStreamCallbacks,
  ): Promise<void> {
    return consumeInterviewStream({
      url: `${BASE_PATH}/${sessionId}/answers/stream`,
      payload,
      timeoutMillis: ANSWER_STREAM_TIMEOUT_MS,
      callbacks: {
        onStage: content => callbacks.onStage(content.trim() as AnswerStreamStage),
        onDelta: callbacks.onDelta,
        onDone: callbacks.onDone,
        onError: callbacks.onError,
      },
    });
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

  selectProblemVariant(sessionId: string, problemId: string): Promise<PublicAlgorithmProblem> {
    return request.get<PublicAlgorithmProblem>(
      `${BASE_PATH}/${sessionId}/algorithm/problems/${encodeURIComponent(problemId)}/variant`,
    );
  },

};

interface InterviewStreamOptions {
  url: string;
  payload: unknown;
  timeoutMillis: number;
  callbacks: {
    onCreated?: (session: AdaptiveInterviewSession) => void;
    onStage?: (content: string) => void;
    onDelta: (delta: string) => void;
    onDone: (session: AdaptiveInterviewSession) => void;
    onError: (error: Error) => void;
  };
}

function consumeInterviewStream(options: InterviewStreamOptions): Promise<void> {
  const controller = new AbortController();
  let timedOut = false;
  let doneReceived = false;
  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, options.timeoutMillis);
  return streamSse({
    url: options.url,
    init: {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${getAccessToken() ?? ''}`,
      },
      body: JSON.stringify(options.payload),
      signal: controller.signal,
    },
    parseMode: 'event',
    onMessage: () => {},
    onEvent: (name, content) => {
      if (name === 'created') {
        options.callbacks.onCreated?.(JSON.parse(content) as AdaptiveInterviewSession);
      } else if (name === 'stage') {
        options.callbacks.onStage?.(content);
      } else if (name === 'delta') {
        options.callbacks.onDelta(content);
      } else if (name === 'done') {
        doneReceived = true;
        options.callbacks.onDone(JSON.parse(content) as AdaptiveInterviewSession);
      }
    },
    onComplete: () => {
      if (!doneReceived) {
        options.callbacks.onError(new Error('连接中断，请刷新后重试'));
      }
    },
    onError: error => {
      options.callbacks.onError(timedOut ? new Error('生成超时，请重试') : error);
    },
  }).finally(() => clearTimeout(timer));
}
