package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="candidate_memory_observation_revisions", uniqueConstraints=
    @UniqueConstraint(columnNames={"episode_id","revision"}))
public class MemoryObservationRevision {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(name="episode_id",nullable=false) public long episodeId;
  @Column(nullable=false) public long revision;
  @Column(length=64) public String tenantId;
  @Column(nullable=false,length=64) public String candidateId;
  @Column(nullable=false,length=36) public String sessionId;
  @Column(nullable=false,length=16) public String sessionMode;
  @Column(nullable=false,length=64) public String skillId;
  @Column(nullable=false,length=64) public String focusId;
  @Column(nullable=false,length=64) public String capabilityKey;
  @Column(nullable=false,columnDefinition="TEXT") public String objective;
  @Column(nullable=false,length=100) public String opportunityKey;
  @Column(nullable=false) public boolean independent;
  @Column(nullable=false) public boolean retracted;
  @Column(columnDefinition="TEXT") public String reason;
  @Column(nullable=false,length=64) public String inputFingerprint;
  @Column(nullable=false,length=40) public String rulesVersion;
  @Column(length=160) public String provider;
  @Column(nullable=false,columnDefinition="TEXT") public String sourceContextJson;
  @Column(nullable=false,columnDefinition="TEXT") public String observationJson;
  @Column(nullable=false,columnDefinition="TEXT") public String summary;
  @Column(nullable=false) public LocalDateTime createdAt;
}
