export type AuditDecision = 'DELETE' | 'SIMPLIFY' | 'KEEP' | 'REDESIGN';

export type AuditSeverity = 'P0' | 'P1' | 'P2' | 'P3';

/** essential = 业务必要复杂度；accidental = 偶然复杂度；mixed = 两者交织 */
export type ComplexityKind = 'essential' | 'accidental' | 'mixed';

export interface RootCause {
  id: string;
  index: string;
  title: string;
  thesis: string;
  severity: AuditSeverity;
  decision: AuditDecision;
  kind: ComplexityKind;
  modules: readonly string[];
  secondaryComplexity: readonly string[];
  impacts: readonly string[];
  recommendation: string;
  evidenceIds: readonly string[];
}

export interface CodeEvidence {
  id: string;
  rootCauseId: string;
  severity: AuditSeverity;
  /** 审计原文中的优先级标注，如 "P0/P1" */
  severityLabel?: string;
  title: string;
  /** 代码证据引用，形如 "path:line"；已按生产代码复核修正 */
  refs: readonly string[];
  finding: string;
  impact: string;
  fix: string;
  tags: readonly string[];
}

export interface DecisionItem {
  id: string;
  rootCauseId: string;
  title: string;
  decision: AuditDecision;
  stage: AuditSeverity;
  reason: string;
  replacement: string;
  risk: string;
}

export interface Hotspot {
  label: string;
  lines: number;
  disposition: string;
  decision: AuditDecision;
  kind: ComplexityKind;
}

export interface MatrixItem {
  id: string;
  label: string;
  impact: number;
  cost: number;
  stage: AuditSeverity;
  decision: AuditDecision;
}

export interface RoadmapPhase {
  id: string;
  title: string;
  objective: string;
  changes: readonly string[];
  tests: readonly string[];
}

export interface ArchNode {
  name: string;
  note?: string;
  tone: 'fact' | 'risk' | 'compute' | 'side-effect' | 'removed';
}

export interface ArchitectureLane {
  label: string;
  nodes: readonly ArchNode[];
}

export interface StateMachineRow {
  name: string;
  category: string;
  decision: string;
  note: string;
}

export interface FactSourceRow {
  fact: string;
  current: string;
  recommended: string;
  removable: string;
}

/** cls: A 必要防线 / B 体验或成本优化 / C 重复 correctness 保护 / D 无当前价值 */
export interface ValidationRow {
  invariant: string;
  current: string;
  needed: string;
  duplicate: string;
}
