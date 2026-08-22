package interview.guide.modules.interviewschedule.service;

import interview.guide.modules.interviewschedule.model.InterviewScheduleFilter;
import interview.guide.modules.interviewschedule.repository.InterviewScheduleRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewScheduleServiceTest {

  @Mock
  private InterviewScheduleRepository repository;

  @Test
  @DisplayName("日程列表只查询当前候选人的记录")
  void shouldListOnlyCandidateSchedules() {
    UUID candidateId = UUID.randomUUID();
    when(repository.findAllByCandidateIdOrderByInterviewTimeAsc(candidateId))
        .thenReturn(List.of());
    InterviewScheduleService service = new InterviewScheduleService(repository);

    var result = service.getAll(candidateId, new InterviewScheduleFilter(null, null, null));

    assertThat(result).isEmpty();
    verify(repository).findAllByCandidateIdOrderByInterviewTimeAsc(candidateId);
  }
}
