import type { AdaptiveDepthLevel } from './adaptiveInterview';

export type SemanticAbility = 'WEAK' | 'COMPETENT' | 'PROFICIENT';
export type MemoryTagCategory = 'ERROR_PATTERN' | 'ANSWER_HABIT';
export type EpisodeEnrichmentStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'
  | 'LEGACY_UNENRICHED';

export interface CandidateMemoryTagCount {
  category: MemoryTagCategory;
  tag: string;
  count: number;
}

export interface CandidateMemoryTopic {
  skillId: string;
  focusId: string;
  ability: SemanticAbility;
  l0Count: number;
  l1Count: number;
  l2Count: number;
  l3Count: number;
  l4Count: number;
  tagCounts: CandidateMemoryTagCount[];
}

export interface CandidateMemoryEpisode {
  sessionId: string;
  turnIndex: number;
  parentTurnIndex: number | null;
  skillId: string;
  focusId: string;
  depthLevel: AdaptiveDepthLevel;
  enrichmentStatus: EpisodeEnrichmentStatus;
  createdAt: string;
}

export interface CandidateMemoryEpisodePage {
  content: CandidateMemoryEpisode[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface CandidateMemoryResponse {
  candidateId: string;
  topics: CandidateMemoryTopic[];
  episodes: CandidateMemoryEpisodePage;
}
