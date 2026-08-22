package interview.guide.modules.interview.agent.adaptive.algorithm.problem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 算法题目 JPA 实体。
 */
@Entity
@Table(name = "algorithm_problems")
public class AlgorithmProblemEntity {

  @Id
  @Column(length = 64)
  private String id;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String statement;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private AlgorithmDifficulty difficulty;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String tags;

  @Column(name = "sample_cases_ref", nullable = false, length = 512)
  private String sampleCasesRef;

  @Column(name = "hidden_cases_ref", nullable = false, length = 512)
  private String hiddenCasesRef;

  @Column(name = "time_limit_ms", nullable = false)
  private int timeLimitMs;

  @Column(name = "memory_limit_kb", nullable = false)
  private int memoryLimitKb;

  @Column(name = "variant_group", nullable = false, length = 64)
  private String variantGroup;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected AlgorithmProblemEntity() {}

  public AlgorithmProblemEntity(AlgorithmProblem problem) {
    id = problem.id();
    title = problem.title();
    statement = problem.statement();
    difficulty = problem.difficulty();
    tags = problem.tags();
    sampleCasesRef = problem.sampleCasesRef();
    hiddenCasesRef = problem.hiddenCasesRef();
    timeLimitMs = problem.timeLimitMs();
    memoryLimitKb = problem.memoryLimitKb();
    variantGroup = problem.variantGroup();
  }

  public AlgorithmProblem toDomain() {
    return new AlgorithmProblem(
        id,
        title,
        statement,
        difficulty,
        tags,
        sampleCasesRef,
        hiddenCasesRef,
        timeLimitMs,
        memoryLimitKb,
        variantGroup
    );
  }

  @PrePersist
  void prePersist() {
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
