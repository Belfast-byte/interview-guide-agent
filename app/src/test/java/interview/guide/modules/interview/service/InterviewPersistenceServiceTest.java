package interview.guide.modules.interview.service;

import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.repository.InterviewAnswerRepository;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import interview.guide.modules.interview.service.InterviewPersistenceService.SaveSessionCommand;
import interview.guide.modules.resume.repository.ResumeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewPersistenceServiceTest {

  @Mock
  private InterviewSessionRepository sessionRepository;

  @Mock
  private InterviewAnswerRepository answerRepository;

  @Mock
  private ResumeRepository resumeRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("知识库面试保存时写入 interviewCategory")
  void shouldSaveInterviewCategoryForKnowledgeBaseSession() {
    InterviewPersistenceService service = newService();
    when(sessionRepository.save(any(InterviewSessionEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.saveSession(new SaveSessionCommand(
        null, "sid1", null, List.of(), "dashscope", "knowledge-base", "mid",
        "KNOWLEDGE_BASE", 9L, "MySQL"));

    ArgumentCaptor<InterviewSessionEntity> captor = ArgumentCaptor.forClass(InterviewSessionEntity.class);
    verify(sessionRepository).save(captor.capture());
    InterviewSessionEntity saved = captor.getValue();
    assertThat(saved.getInterviewCategory()).isEqualTo("MySQL");
    assertThat(saved.getKnowledgeBaseId()).isEqualTo(9L);
    assertThat(saved.getSourceType()).isEqualTo("KNOWLEDGE_BASE");
  }

  @Test
  @DisplayName("普通面试保存时 interviewCategory 保持 null")
  void shouldKeepInterviewCategoryNullForNormalSession() {
    InterviewPersistenceService service = newService();
    UUID candidateId = UUID.randomUUID();
    when(sessionRepository.save(any(InterviewSessionEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.saveSession(new SaveSessionCommand(
        candidateId, "sid2", null, List.of(), "dashscope", "java-backend", "mid",
        "NORMAL", null, null));

    ArgumentCaptor<InterviewSessionEntity> captor = ArgumentCaptor.forClass(InterviewSessionEntity.class);
    verify(sessionRepository).save(captor.capture());
    InterviewSessionEntity saved = captor.getValue();
    assertThat(saved.getInterviewCategory()).isNull();
    assertThat(saved.getSourceType()).isEqualTo("NORMAL");
    assertThat(saved.getKnowledgeBaseId()).isNull();
    assertThat(saved.getCandidateId()).isEqualTo(candidateId);
  }

  @Test
  @DisplayName("候选人查询会把归属条件下推到仓储")
  void shouldQuerySessionWithCandidateOwnership() {
    InterviewPersistenceService service = newService();
    UUID candidateId = UUID.randomUUID();

    service.findBySessionId(candidateId, "sid3");

    verify(sessionRepository).findBySessionIdAndCandidateId("sid3", candidateId);
  }

  private InterviewPersistenceService newService() {
    return new InterviewPersistenceService(
        sessionRepository,
        answerRepository,
        resumeRepository,
        objectMapper
    );
  }
}
