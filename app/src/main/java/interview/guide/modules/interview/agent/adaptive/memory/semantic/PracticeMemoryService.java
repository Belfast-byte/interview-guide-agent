package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeQueryService;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeQueryService.EpisodeView;
import interview.guide.modules.interview.skill.InterviewSkillService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** 职位画像只是正式评估的投影，练习规划同时读取原场景作为出题依据。 */
@Service
@RequiredArgsConstructor
public class PracticeMemoryService {
  public static final int RECALL_SIZE = 5;
  private final EpisodeQueryService episodes;
  private final InterviewSkillService skills;

  public PracticePlanningMemory planning(MemoryOwner owner, PracticeScope scope) {
    return new PracticePlanningMemory(scope.topics().stream()
        .map(topic -> new PracticePlanningMemory.TopicHistory(topic,
            episodes.recent(owner, topic, PageRequest.of(0, RECALL_SIZE))))
        .toList());
  }

  public List<SkillProfile> profile(MemoryOwner owner) {
    var latest = episodes.latest(owner);
    var catalog = skills.getAllSkills().stream().collect(Collectors.toMap(
        InterviewSkillService.SkillDTO::id, Function.identity()));
    var bySkill = latest.stream().collect(Collectors.groupingBy(
        episode -> episode.topic().skillId(), LinkedHashMap::new, Collectors.toList()));
    return bySkill.entrySet().stream().map(entry -> {
      var skill = catalog.get(entry.getKey());
      var topics = new LinkedHashMap<String, TopicProfile>();
      if (skill != null) {
        // 目录中尚未回答的知识点保持无评估，不能填成 L0。
        skill.categories().forEach(category -> topics.put(category.key(),
            new TopicProfile(category.key(), category.label(), null)));
      }
      entry.getValue().forEach(episode -> {
        var known = topics.get(episode.topic().focusId());
        topics.put(episode.topic().focusId(), new TopicProfile(episode.topic().focusId(),
            known == null ? episode.topic().focusId() : known.focusName(), episode));
      });
      // 已移出目录的历史 Skill 仍用原 ID 展示，不猜测新的职位归属。
      return new SkillProfile(entry.getKey(), skill == null ? entry.getKey() : skill.name(),
          List.copyOf(topics.values()));
    }).toList();
  }

  public record SkillProfile(String skillId, String skillName, List<TopicProfile> topics) {}
  public record TopicProfile(String focusId, String focusName, EpisodeView latest) {}

  public record PracticePlanningMemory(List<TopicHistory> topics) {
    public record TopicHistory(TopicKey topic, List<EpisodeView> episodes) {}
  }
}
