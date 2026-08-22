package interview.guide.modules.resume.service;

import interview.guide.infrastructure.file.FileHashService;
import interview.guide.infrastructure.mapper.ResumeMapper;
import interview.guide.modules.resume.repository.ResumeAnalysisRepository;
import interview.guide.modules.resume.repository.ResumeRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResumePersistenceServiceTest {

  @Mock
  private ResumeRepository resumeRepository;
  @Mock
  private ResumeAnalysisRepository analysisRepository;
  @Mock
  private ResumeMapper resumeMapper;
  @Mock
  private FileHashService fileHashService;

  @Test
  @DisplayName("按 ID 查询简历时把候选人归属条件下推到仓储")
  void shouldQueryResumeWithCandidateOwnership() {
    UUID candidateId = UUID.randomUUID();
    ResumePersistenceService service = new ResumePersistenceService(
        resumeRepository,
        analysisRepository,
        new ObjectMapper(),
        resumeMapper,
        fileHashService
    );

    service.findById(candidateId, 7L);

    verify(resumeRepository).findByIdAndCandidateId(7L, candidateId);
  }
}
