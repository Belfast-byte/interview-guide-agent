package interview.guide.modules.auth.persistence;

import interview.guide.modules.auth.domain.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

  @Id
  private UUID id;

  @Column(nullable = false, unique = true, length = 320)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 60)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserRole role;

  @Column(name = "tenant_id", length = 64)
  private String tenantId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected UserEntity() {}

  public UserEntity(String email, String passwordHash, UserRole role) {
    this.id = UUID.randomUUID();
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
  }

  public UUID id() {
    return id;
  }

  public String email() {
    return email;
  }

  public String passwordHash() {
    return passwordHash;
  }

  public UserRole role() {
    return role;
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
