import type { AdaptiveDepthLevel } from './adaptiveInterview';

export type EvaluatedAbility = 'WEAK' | 'COMPETENT' | 'PROFICIENT';
export type PracticeMastery = 'UNRESOLVED' | 'ASSISTED' | 'INDEPENDENT';
export type TransferStatus = 'NOT_REEVALUATED' | 'CONFIRMED' | 'REGRESSED';
export type PracticeOutcome = 'COMPLETED' | 'UNRESOLVED';
export type EpisodeAssistanceLevel = 'NONE' | 'FOLLOW_UP' | 'HINT' | 'TOOL_ASSISTED';
export type MemoryTagCategory = 'ERROR_PATTERN' | 'ANSWER_HABIT';
export type CandidateMemoryTurnTriggerType =
  | 'PLANNED'
  | 'ASSESSMENT_GAP'
  | 'TOOL_RESULT';
export type EpisodeEnrichmentStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'
  | 'LEGACY_UNENRICHED';

export interface CandidateMemoryStablePattern {
  category: MemoryTagCategory;
  tag: string;
  episodeCount: number;
}

export interface SemanticTrackMetadata {
  revision: number;
  stablePatterns: CandidateMemoryStablePattern[];
}

export interface EvaluationMemoryTrack {
  metadata: SemanticTrackMetadata;
  ability: EvaluatedAbility;
  statistics: { levelCounts: number[] };
}

export interface PracticeMemoryTrack {
  metadata: SemanticTrackMetadata;
  mastery: PracticeMastery;
  details: {
    statistics: {
      completedByAssistance: Partial<Record<EpisodeAssistanceLevel, number>>;
      unresolvedCount: number;
    };
    latest: {
      episodeId: number;
      result: {
        outcome: PracticeOutcome;
        assistance: EpisodeAssistanceLevel;
        targetDepth: AdaptiveDepthLevel;
      };
    };
    transfer: {
      status: TransferStatus;
      confirmedByEpisodeId: number | null;
    };
  };
}

export interface CandidateMemoryTopic {
  skillId: string;
  focusId: string;
  evaluation: EvaluationMemoryTrack | null;
  practice: PracticeMemoryTrack | null;
}

export interface CandidateMemoryEpisode {
  sessionId: string;
  turnIndex: number;
  parentTurnIndex: number | null;
  triggerType: CandidateMemoryTurnTriggerType;
  skillId: string;
  focusId: string;
  depthLevel: AdaptiveDepthLevel;
  enrichmentStatus: EpisodeEnrichmentStatus;
  createdAt: string;
}

export interface CandidateMemoryEpisodePage {
  content: CandidateMemoryEpisode[];
  ancestors: CandidateMemoryEpisode[];
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
