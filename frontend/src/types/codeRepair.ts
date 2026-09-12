import type { AdaptiveDepthLevel } from './adaptiveInterview';

export interface CodeRepairTask {
  initialCode: string;
  requirements: string[];
  assumptions: string[];
}
export interface SourceQuote {
  source: 'ANSWER_TEXT' | 'SUBMITTED_CODE';
  quote: string;
  startOffset?: number | null;
}
export interface QuoteLocator {
  source: SourceQuote['source'];
  startOffset: number;
  endOffset: number;
}
export interface CodeRepairReview {
  checks: { checkId: string; result: 'SATISFIED' | 'NOT_SATISFIED' | 'UNDETERMINED'; reason: string }[];
}
export interface AssessmentFeedback {
  depthLevel: AdaptiveDepthLevel;
  rationale: string;
  codeReview?: CodeRepairReview | null;
  evidenceQuotes: SourceQuote[];
}
export interface CodeRepairFields {
  questionType?: 'TEXT' | 'CODE_REPAIR';
  codeTaskTurnIndex?: number | null;
  codeTask?: CodeRepairTask | null;
  submittedCode?: string | null;
  codeReview?: CodeRepairReview | null;
  assessmentFeedback?: AssessmentFeedback | null;
}
