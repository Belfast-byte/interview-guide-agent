import type {
  CandidateMemoryEpisode,
  EpisodeEnrichmentStatus,
  SemanticAbility,
} from '../../types/candidateMemory';

export interface CandidateMemoryEpisodeNode {
  episode: CandidateMemoryEpisode;
  children: CandidateMemoryEpisodeNode[];
}

const ENRICHMENT_STATUS_LABELS: Record<EpisodeEnrichmentStatus, string> = {
  PENDING: '等待补全',
  PROCESSING: '补全中',
  COMPLETED: '已补全',
  FAILED: '补全失败',
  LEGACY_UNENRICHED: '历史数据未补全',
};

const ABILITY_LABELS: Record<SemanticAbility, string> = {
  WEAK: '待加强',
  COMPETENT: '已掌握',
  PROFICIENT: '熟练',
};

export function getEnrichmentStatusLabel(status: EpisodeEnrichmentStatus): string {
  return ENRICHMENT_STATUS_LABELS[status];
}

export function getAbilityLabel(ability: SemanticAbility): string {
  return ABILITY_LABELS[ability];
}

export function buildEpisodeChains(
  episodes: readonly CandidateMemoryEpisode[],
): CandidateMemoryEpisodeNode[] {
  const nodes = new Map<string, CandidateMemoryEpisodeNode>();
  episodes.forEach(episode => {
    nodes.set(episodeKey(episode.sessionId, episode.turnIndex), {
      episode,
      children: [],
    });
  });

  const roots: CandidateMemoryEpisodeNode[] = [];
  episodes.forEach(episode => attachEpisode(nodes, episode, roots));
  return roots;
}

function attachEpisode(
  nodes: Map<string, CandidateMemoryEpisodeNode>,
  episode: CandidateMemoryEpisode,
  roots: CandidateMemoryEpisodeNode[],
): void {
  const node = nodes.get(episodeKey(episode.sessionId, episode.turnIndex))!;
  const parent = episode.parentTurnIndex === null
    ? undefined
    : nodes.get(episodeKey(episode.sessionId, episode.parentTurnIndex));
  if (parent) {
    parent.children.push(node);
    return;
  }
  roots.push(node);
}

function episodeKey(sessionId: string, turnIndex: number): string {
  return `${sessionId}:${turnIndex}`;
}
