export type AdaptiveSessionStatus = 'CREATED' | 'IN_PROGRESS' | 'COMPLETED';
export type AdaptiveDimensionStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';
export type AdaptiveDepthLevel = 'L0' | 'L1' | 'L2' | 'L3' | 'L4';

export interface AdaptiveInterviewDimension {
  order: number;
  dimension: string;
  focus: string;
  allocatedTurns: number;
  completedTurns: number;
  status: AdaptiveDimensionStatus;
}

export interface AdaptiveInterviewTurn {
  turnIndex: number;
  dimensionOrder: number;
  question: string;
  answer: string | null;
}

export interface AdaptiveInterviewSession {
  sessionId: string;
  runtimeVersion: string;
  status: AdaptiveSessionStatus;
  currentTurn: number;
  maxTurns: number;
  currentQuestion: string | null;
  dimensions: AdaptiveInterviewDimension[];
  turns: AdaptiveInterviewTurn[];
}

export interface CreateAdaptiveInterviewRequest {
  candidateId: string;
  jd: string;
  resume: string;
  llmProvider?: string;
}

export interface SubmitAdaptiveAnswerRequest {
  turnIndex: number;
  answer: string;
  codeSubmission?: {
    problemId?: string;
    scenarioId?: string;
    language: SandboxLanguage;
    runMode: SandboxRunMode;
  };
}

export interface AdaptiveEvidenceReference {
  type: 'QUOTE' | 'TOOL_RESULT';
  turnIndex: number;
  question: string;
  answer: string;
  quote: string | null;
  toolResult: {
    toolCallId: number | null;
    sandboxExecutionId: string | null;
    toolName: string;
    resultId: string;
    output: string;
  } | null;
}

export type SandboxLanguage = 'JAVA' | 'PYTHON' | 'CPP';
export type SandboxRunMode = 'SAMPLE' | 'FULL';
export type SandboxExecutionStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'TIMEOUT_QUEUED';
export type SandboxVerdict = 'AC' | 'WA' | 'CE' | 'TLE' | 'MLE' | 'RE' | 'IE';
export type SandboxPolicyViolation = 'NETWORK_ACCESS' | 'FILESYSTEM_ACCESS' | 'PROCESS_LIMIT' | 'OUTPUT_LIMIT';

export interface PublicAlgorithmProblem {
  id: string;
  title: string;
  statement: string;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
  tags: string;
  sampleCases: string;
}

export interface SubmitAlgorithmCodeRequest {
  turnIndex: number;
  problemId: string;
  language: SandboxLanguage;
  source: string;
  runMode: SandboxRunMode;
}

export interface SandboxExecution {
  submissionId: string;
  submissionSeq: number;
  runMode: SandboxRunMode;
  status: SandboxExecutionStatus;
  verdict: SandboxVerdict | null;
  passed: number | null;
  total: number | null;
  timeMs: number | null;
  memoryKb: number | null;
  firstFailedCase: number | null;
  pendingRejudge: boolean;
  policyViolation: SandboxPolicyViolation | null;
}

export interface ToolResultFollowUp {
  resultId: string;
  turnIndex: number;
  responseContent: string;
  completedAt: string;
}

export interface AdaptiveDimensionConclusion {
  order: number;
  dimension: string;
  focus: string;
  depthLevel: AdaptiveDepthLevel;
  confidence: number;
  rationale: string;
  evidences: AdaptiveEvidenceReference[];
}

export interface AdaptiveWeakPoint {
  dimension: string;
  demonstratedLevel: AdaptiveDepthLevel;
  missingLevel: AdaptiveDepthLevel;
  missingCapability: string;
}

export interface AdaptivePracticeRecommendation {
  dimensionOrder: number;
  dimension: string;
  demonstratedLevel: AdaptiveDepthLevel;
  questionSourceId: string;
  questionDifficulty: string;
  question: string;
  status: 'PENDING' | 'COMPLETED';
}

export interface AdaptiveAssessmentReport {
  sessionId: string;
  dimensions: AdaptiveDimensionConclusion[];
  weakPoints: AdaptiveWeakPoint[];
  practiceRecommendations: AdaptivePracticeRecommendation[];
}
