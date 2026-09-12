import type { CodeRepairFields } from '../../types/codeRepair';
import JavaEditor from './JavaEditor';
import CodeRepairPanel, { CodeTaskBrief, OriginalTaskCode } from './CodeRepairPanel';
import CodeReviewFeedback from './CodeReviewFeedback';

export default function CodeRepairHistory({ turn }: { turn: CodeRepairFields & { turnIndex?: number; answer?: string | null } }) {
  const currentLabel = turn.turnIndex == null ? '本轮提交代码' : `第 ${turn.turnIndex} 轮提交代码`;
  if (!turn.codeTask) return turn.submittedCode != null ? <div className="mt-4 space-y-4">
    <p className="wk-label">{currentLabel}</p>
    <JavaEditor code={turn.submittedCode} readOnly />
    <CodeReviewFeedback review={turn.codeReview} code={turn.submittedCode} answer={turn.answer} />
  </div> : null;
  return <div className="mt-4 space-y-4">
    <CodeTaskBrief task={turn.codeTask} />
    {turn.submittedCode != null
      ? <CodeRepairPanel task={turn.codeTask} code={turn.submittedCode} currentLabel={currentLabel} readOnly />
      : <OriginalTaskCode code={turn.codeTask.initialCode} />}
    <CodeReviewFeedback review={turn.codeReview} feedback={turn.assessmentFeedback} code={turn.submittedCode} answer={turn.answer} />
  </div>;
}
