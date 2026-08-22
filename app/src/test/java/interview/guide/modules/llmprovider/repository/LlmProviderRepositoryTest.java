package interview.guide.modules.llmprovider.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.auth.domain.UserRole;
import interview.guide.modules.auth.persistence.UserEntity;
import interview.guide.modules.auth.persistence.UserRepository;
import interview.guide.modules.llmprovider.model.LlmProviderEntity;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("Provider 归属查询测试")
class LlmProviderRepositoryTest {

  @Autowired
  private LlmProviderRepository providerRepository;

  @Autowired
  private UserRepository userRepository;

  private UUID candidateA;
  private UUID candidateB;

  @BeforeEach
  void setUp() {
    candidateA = saveUser("candidate-a@example.com");
    candidateB = saveUser("candidate-b@example.com");
  }

  @Test
  @DisplayName("系统查询不返回候选人 Provider")
  void systemQueryExcludesCandidateProviders() {
    providerRepository.save(provider("system-provider", null, null));
    providerRepository.save(provider("candidate-provider", candidateA, "我的模型"));

    assertThat(providerRepository.findByCandidateIdIsNullOrderByIdAsc())
        .extracting(LlmProviderEntity::getId)
        .containsExactly("system-provider");
  }

  @Test
  @DisplayName("不同候选人可以使用相同展示名称")
  void differentCandidatesCanReuseDisplayName() {
    providerRepository.save(provider("provider-a", candidateA, "日常面试"));
    providerRepository.saveAndFlush(provider("provider-b", candidateB, "日常面试"));

    assertThat(providerRepository.findByCandidateIdOrderByCreatedAtDesc(candidateA))
        .extracting(LlmProviderEntity::getId)
        .containsExactly("provider-a");
    assertThat(providerRepository.findByCandidateIdOrderByCreatedAtDesc(candidateB))
        .extracting(LlmProviderEntity::getId)
        .containsExactly("provider-b");
  }

  @Test
  @DisplayName("同一候选人的展示名称必须唯一")
  void displayNameMustBeUniqueWithinCandidate() {
    providerRepository.saveAndFlush(provider("provider-a", candidateA, "日常面试"));

    assertThatThrownBy(() -> providerRepository.saveAndFlush(
        provider("provider-b", candidateA, "日常面试")
    )).isInstanceOf(DataIntegrityViolationException.class);
  }

  private UUID saveUser(String email) {
    return userRepository.save(
        new UserEntity(email, "password-hash", UserRole.CANDIDATE)
    ).id();
  }

  private LlmProviderEntity provider(String id, UUID candidateId, String displayName) {
    return LlmProviderEntity.builder()
        .id(id)
        .candidateId(candidateId)
        .displayName(displayName)
        .baseUrl("https://example.com/v1")
        .apiKeyCiphertext("ciphertext")
        .apiKeyNonce("nonce")
        .model("chat-model")
        .supportsEmbedding(false)
        .enabled(true)
        .builtin(candidateId == null)
        .build();
  }
}
