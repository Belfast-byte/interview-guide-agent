import { useEffect, useState } from 'react';
import AppendixTables from './components/AppendixTables';
import ArchitectureComparison from './components/ArchitectureComparison';
import AuditHeader from './components/AuditHeader';
import AuditRail from './components/AuditRail';
import ComplexityHotspots from './components/ComplexityHotspots';
import DecisionLedger from './components/DecisionLedger';
import EvidenceExplorer from './components/EvidenceExplorer';
import ImpactCostMatrix from './components/ImpactCostMatrix';
import Roadmap from './components/Roadmap';
import RootCauseMap from './components/RootCauseMap';
import {
  AFTER_ARCHITECTURE,
  BEFORE_ARCHITECTURE,
  DECISIONS,
  FACT_SOURCES,
  HOTSPOTS,
  MATRIX_ITEMS,
  ROADMAP,
  STATE_MACHINES,
  VALIDATION_MATRIX,
} from './data/auditDetails';
import { CODE_EVIDENCE } from './data/evidence';
import { ROOT_CAUSES } from './data/rootCauses';

const AUDIT_DOCUMENT = 'docs/review/8.29-review.md';

function useAuditDocumentTitle() {
  useEffect(() => {
    const previous = document.title;
    document.title = 'Agent Runtime 复杂度审计';
    return () => {
      document.title = previous;
    };
  }, []);
}

function AuditFooter() {
  return (
    <footer className="mt-4 border-t border-zinc-800 pt-4 pb-2">
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1 text-[12px] text-zinc-500">
        <span>完整审计基线</span>
        <code className="font-mono text-[11px] text-zinc-400">{AUDIT_DOCUMENT}</code>
      </div>
      <p className="mt-1.5 text-[11px] leading-relaxed text-zinc-600">
        判断来自生产代码，关键引用已逐条复核；行数是审计范围，不是删除承诺。业务必要复杂度（Keep）与偶然复杂度在全站显式区分。
      </p>
    </footer>
  );
}

export default function ArchitectureAuditPage() {
  useAuditDocumentTitle();
  const [selectedCause, setSelectedCause] = useState(ROOT_CAUSES[0].id);
  const [evidenceCause, setEvidenceCause] = useState('ALL');

  return (
    <div className="min-h-screen bg-[#09090b] font-sans text-zinc-300 antialiased">
      <a
        href="#audit-content"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded focus:bg-zinc-800 focus:px-3 focus:py-2 focus:text-[13px] focus:text-zinc-100"
      >
        跳到审计正文
      </a>
      <div className="mx-auto max-w-6xl px-4 pb-16 sm:px-6 lg:grid lg:grid-cols-[13rem_minmax(0,1fr)] lg:gap-10 lg:px-8">
        <AuditRail />
        <main id="audit-content" tabIndex={-1} className="space-y-14 pt-6 lg:pt-10">
          <AuditHeader />
          <ComplexityHotspots hotspots={HOTSPOTS} />
          <RootCauseMap
            causes={ROOT_CAUSES}
            evidence={CODE_EVIDENCE}
            selectedId={selectedCause}
            onSelect={setSelectedCause}
            onViewEvidence={setEvidenceCause}
          />
          <ArchitectureComparison before={BEFORE_ARCHITECTURE} after={AFTER_ARCHITECTURE} />
          <DecisionLedger decisions={DECISIONS} causes={ROOT_CAUSES} />
          <ImpactCostMatrix items={MATRIX_ITEMS} />
          <Roadmap phases={ROADMAP} />
          <EvidenceExplorer
            evidence={CODE_EVIDENCE}
            causes={ROOT_CAUSES}
            rootCauseFilter={evidenceCause}
            onRootCauseFilterChange={setEvidenceCause}
          />
          <AppendixTables
            stateMachines={STATE_MACHINES}
            factSources={FACT_SOURCES}
            validationMatrix={VALIDATION_MATRIX}
          />
          <AuditFooter />
        </main>
      </div>
    </div>
  );
}
