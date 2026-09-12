package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.application.CandidateMemoryQueryService.MemoryPage;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeQueryService.EpisodeView;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMemoryService.SkillProfile;
import java.util.List;

/** 显式分页协议；嵌套视图只含问答事实，不序列化持久化实体。 */
public record CandidateMemoryResponse(
    String candidateId, List<SkillProfile> skills, EpisodePage episodes
) {
  static CandidateMemoryResponse from(MemoryPage source) {
    var page = source.episodes();
    return new CandidateMemoryResponse(source.candidateId(), source.skills(),
        new EpisodePage(page.getContent(), page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages(), page.isLast()));
  }

  public record EpisodePage(List<EpisodeView> content, int page, int size,
      long totalElements, int totalPages, boolean last) {}
}
