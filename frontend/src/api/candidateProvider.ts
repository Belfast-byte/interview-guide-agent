import request from './request';
import type {
  CandidateProvider,
  CandidateProviderRequest,
  CandidateProviderTestResult,
} from '../types/candidateProvider';

const BASE_PATH = '/api/me/llm-providers';

export const candidateProviderApi = {
  list: (): Promise<CandidateProvider[]> => request.get<CandidateProvider[]>(BASE_PATH),

  create: (payload: CandidateProviderRequest): Promise<void> =>
    request.post<void>(BASE_PATH, payload),

  update: (providerId: string, payload: CandidateProviderRequest): Promise<void> =>
    request.put<void>(`${BASE_PATH}/${providerId}`, payload),

  delete: (providerId: string): Promise<void> =>
    request.delete<void>(`${BASE_PATH}/${providerId}`),

  test: (providerId: string): Promise<CandidateProviderTestResult> =>
    request.post<CandidateProviderTestResult>(`${BASE_PATH}/${providerId}/test`),

  setDefaultChat: (providerId: string): Promise<void> =>
    request.put<void>(`${BASE_PATH}/${providerId}/default-chat`),

  setDefaultEmbedding: (providerId: string): Promise<void> =>
    request.put<void>(`${BASE_PATH}/${providerId}/default-embedding`),
};
