package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeTagRepository extends JpaRepository<EpisodeTagEntity, Long> {

  List<EpisodeTagEntity> findByEpisodeIdOrderById(Long episodeId);

  void deleteByEpisodeId(Long episodeId);
}
