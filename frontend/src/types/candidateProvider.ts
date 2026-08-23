export interface CandidateProvider {
  id: string;
  displayName: string;
  baseUrl: string;
  maskedApiKey: string;
  model: string;
  embeddingModel: string | null;
  embeddingDimensions: number | null;
  supportsEmbedding: boolean;
  temperature: number | null;
  thinkingDisabled: boolean;
  defaultChatProvider: boolean;
  defaultEmbeddingProvider: boolean;
}

export interface CandidateProviderRequest {
  displayName: string;
  baseUrl: string;
  apiKey: string;
  model: string;
  embeddingModel?: string;
  embeddingDimensions?: number;
  temperature?: number;
  thinkingDisabled: boolean;
}

export interface CandidateProviderTestResult {
  success: boolean;
  message: string;
  model: string;
}
