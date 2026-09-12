import type { AdaptiveDepthLevel } from './adaptiveInterview';

export interface CandidateMemoryGap {
  gapId: number;
  anchor: string;
  missingPoint: string;
  closedByAssessmentId: number | null;
  closureEvidenceQuote: string | null;
  closureSummary: string | null;
}

export interface CandidateMemoryEpisode {
  episodeId: number;
  sessionId: string;
  turnIndex: number;
  sessionMode: 'EVALUATION' | 'PRACTICE';
  assessmentId: number;
  topic: { skillId: string; focusId: string };
  question: string;
  answer: string;
  depthLevel: AdaptiveDepthLevel;
  expectedDepth: AdaptiveDepthLevel | null;
  rationaleSummary: string;
  gaps: CandidateMemoryGap[];
  triggerType: 'PLANNED' | 'ASSESSMENT_GAP' | 'AGENT_DECISION';
  priorTurns: { turnIndex: number; question: string; answer: string }[];
  createdAt: string;
}

export interface CandidateMemoryTopic {
  focusId: string;
  focusName: string;
  latest: CandidateMemoryEpisode | null;
}

export interface CandidateMemorySkill {
  skillId: string;
  skillName: string;
  topics: CandidateMemoryTopic[];
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
  skills: CandidateMemorySkill[];
  episodes: CandidateMemoryEpisodePage;
}
