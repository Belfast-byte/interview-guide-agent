package interview.guide.modules.voiceinterview.service;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.modules.resume.repository.ResumeRepository;
import interview.guide.modules.voiceinterview.config.VoiceInterviewProperties;
import interview.guide.modules.voiceinterview.listener.VoiceEvaluateStreamProducer;
import interview.guide.modules.voiceinterview.repository.VoiceInterviewEvaluationRepository;
import interview.guide.modules.voiceinterview.repository.VoiceInterviewMessageRepository;
import interview.guide.modules.voiceinterview.repository.VoiceInterviewSessionRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VoiceInterviewOwnershipServiceTest {

  @Mock private VoiceInterviewSessionRepository sessionRepository;
  @Mock private VoiceInterviewMessageRepository messageRepository;
  @Mock private VoiceInterviewEvaluationRepository evaluationRepository;
  @Mock private RedissonClient redissonClient;
  @Mock private VoiceInterviewProperties properties;
  @Mock private VoiceEvaluateStreamProducer evaluateProducer;
  @Mock private LlmProviderRegistry llmProviderRegistry;
  @Mock private ResumeRepository resumeRepository;

  @Test
  @DisplayName("语音会话读取把候选人归属条件下推到仓储")
  void shouldQuerySessionWithCandidateOwnership() {
    UUID candidateId = UUID.randomUUID();

    assertThatThrownBy(() -> service().getSession(candidateId, 12L))
        .isInstanceOf(RuntimeException.class);

    verify(sessionRepository).findByIdAndCandidateId(12L, candidateId);
  }

  private VoiceInterviewService service() {
    return new VoiceInterviewService(
        sessionRepository,
        messageRepository,
        evaluationRepository,
        redissonClient,
        properties,
        evaluateProducer,
        llmProviderRegistry,
        resumeRepository
    );
  }
}
