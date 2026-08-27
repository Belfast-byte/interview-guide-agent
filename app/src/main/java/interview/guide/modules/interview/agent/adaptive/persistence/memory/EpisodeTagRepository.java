package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EpisodeTagRepository extends JpaRepository<EpisodeTagEntity, Long> {

  List<EpisodeTagEntity> findByEpisodeIdOrderById(Long episodeId);

  @Modifying(flushAutomatically = true)
  @Query("DELETE FROM EpisodeTagEntity tag WHERE tag.episode.id = :episodeId")
  int deleteByEpisodeId(@Param("episodeId") Long episodeId);
}
