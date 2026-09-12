package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeQueryService;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeQueryService.EpisodeView;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMemoryService;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMemoryService.SkillProfile;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 记忆页与 Agent 复用同一份问答、评级和职位画像。 */
@Service
@RequiredArgsConstructor
public class CandidateMemoryQueryService {
  public static final int PAGE_SIZE = 20;
  private final PracticeMemoryService memory;
  private final EpisodeQueryService episodes;

  @Transactional(readOnly = true)
  public MemoryPage read(MemoryOwner owner, int page) {
    return new MemoryPage(owner.candidateId(), memory.profile(owner),
        episodes.page(owner, PageRequest.of(page, PAGE_SIZE)));
  }

  public record MemoryPage(String candidateId, List<SkillProfile> skills, Page<EpisodeView> episodes) {}
}
