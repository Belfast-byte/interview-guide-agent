import type {
  CandidateMemoryEpisode,
  CandidateMemoryTurnTriggerType,
  EpisodeEnrichmentStatus,
  EvaluatedAbility,
} from '../../types/candidateMemory';

export interface CandidateMemoryEpisodeNode {
  episode: CandidateMemoryEpisode;
  contextOnly: boolean;
  children: CandidateMemoryEpisodeNode[];
}

const ENRICHMENT_STATUS_LABELS: Record<EpisodeEnrichmentStatus, string> = {
  PENDING: '等待补全',
  PROCESSING: '补全中',
  COMPLETED: '已补全',
  FAILED: '补全失败',
  LEGACY_UNENRICHED: '历史数据未补全',
};

const ABILITY_LABELS: Record<EvaluatedAbility, string> = {
  WEAK: '待加强',
  COMPETENT: '已掌握',
  PROFICIENT: '熟练',
};

const TRIGGER_LABELS: Record<CandidateMemoryTurnTriggerType, string> = {
  PLANNED: '起始问题',
  ASSESSMENT_GAP: '能力缺口追问',
  TOOL_RESULT: '工具结果追问',
};

export function getEnrichmentStatusLabel(status: EpisodeEnrichmentStatus): string {
  return ENRICHMENT_STATUS_LABELS[status];
}

export function getAbilityLabel(ability: EvaluatedAbility): string {
  return ABILITY_LABELS[ability];
}

export function getEpisodeTriggerLabel(trigger: CandidateMemoryTurnTriggerType): string {
  return TRIGGER_LABELS[trigger];
}

export function buildEpisodeChains(
  episodes: readonly CandidateMemoryEpisode[],
  ancestors: readonly CandidateMemoryEpisode[] = [],
): CandidateMemoryEpisodeNode[] {
  const contentKeys = new Set(episodes.map(keyOf));
  const allEpisodes = uniqueEpisodes([...episodes, ...ancestors]);
  const nodes = new Map<string, CandidateMemoryEpisodeNode>();
  allEpisodes.forEach(episode => {
    nodes.set(keyOf(episode), {
      episode,
      contextOnly: !contentKeys.has(keyOf(episode)),
      children: [],
    });
  });

  allEpisodes.forEach(episode => attachEpisode(nodes, episode));
  return pageRoots(episodes, nodes);
}

function attachEpisode(
  nodes: Map<string, CandidateMemoryEpisodeNode>,
  episode: CandidateMemoryEpisode,
): void {
  const node = nodes.get(keyOf(episode))!;
  const parent = episode.parentTurnIndex === null
    ? undefined
    : nodes.get(episodeKey(episode.sessionId, episode.parentTurnIndex));
  if (parent) {
    parent.children.push(node);
  }
}

function pageRoots(
  episodes: readonly CandidateMemoryEpisode[],
  nodes: Map<string, CandidateMemoryEpisodeNode>,
): CandidateMemoryEpisodeNode[] {
  const roots = new Map<string, CandidateMemoryEpisodeNode>();
  episodes.forEach(episode => {
    const root = findRoot(nodes.get(keyOf(episode))!, nodes);
    roots.set(keyOf(root.episode), root);
  });
  return [...roots.values()];
}

function findRoot(
  source: CandidateMemoryEpisodeNode,
  nodes: Map<string, CandidateMemoryEpisodeNode>,
): CandidateMemoryEpisodeNode {
  let current = source;
  while (current.episode.parentTurnIndex !== null) {
    const parent = nodes.get(episodeKey(
      current.episode.sessionId,
      current.episode.parentTurnIndex,
    ));
    if (!parent) {
      return current;
    }
    current = parent;
  }
  return current;
}

function uniqueEpisodes(
  episodes: readonly CandidateMemoryEpisode[],
): CandidateMemoryEpisode[] {
  return [...new Map(episodes.map(episode => [keyOf(episode), episode])).values()];
}

function keyOf(episode: CandidateMemoryEpisode): string {
  return episodeKey(episode.sessionId, episode.turnIndex);
}

function episodeKey(sessionId: string, turnIndex: number): string {
  return `${sessionId}:${turnIndex}`;
}
