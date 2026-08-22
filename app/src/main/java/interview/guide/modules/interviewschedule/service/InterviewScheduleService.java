package interview.guide.modules.interviewschedule.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interviewschedule.model.CreateInterviewRequest;
import interview.guide.modules.interviewschedule.model.InterviewScheduleDTO;
import interview.guide.modules.interviewschedule.model.InterviewScheduleEntity;
import interview.guide.modules.interviewschedule.model.InterviewScheduleFilter;
import interview.guide.modules.interviewschedule.model.InterviewStatus;
import interview.guide.modules.interviewschedule.repository.InterviewScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterviewScheduleService {

    private final InterviewScheduleRepository repository;

    private static final String[] COPYABLE_FIELDS = {
        "companyName", "position", "interviewTime", "interviewType",
        "meetingLink", "roundNumber", "interviewer", "notes"
    };

    @Transactional
    public InterviewScheduleDTO create(UUID candidateId, CreateInterviewRequest request) {
        InterviewScheduleEntity entity = new InterviewScheduleEntity();
        BeanUtils.copyProperties(request, entity);
        entity.setCandidateId(candidateId);
        entity.setStatus(InterviewStatus.PENDING);

        return toDTO(repository.save(entity));
    }

    @Transactional
    public InterviewScheduleDTO update(UUID candidateId, Long id, CreateInterviewRequest request) {
        InterviewScheduleEntity entity = getByIdOrThrow(candidateId, id);
        BeanUtils.copyProperties(request, entity, "id", "status");
        return toDTO(repository.save(entity));
    }

    @Transactional
    public void delete(UUID candidateId, Long id) {
        repository.delete(getByIdOrThrow(candidateId, id));
    }

    @Transactional
    public InterviewScheduleDTO updateStatus(UUID candidateId, Long id, InterviewStatus status) {
        InterviewScheduleEntity entity = getByIdOrThrow(candidateId, id);
        entity.setStatus(status);
        return toDTO(repository.save(entity));
    }

    public List<InterviewScheduleDTO> getAll(UUID candidateId, InterviewScheduleFilter filter) {
        List<InterviewScheduleEntity> entities;

        if (filter.start() != null && filter.end() != null) {
            entities = repository.findByCandidateIdAndInterviewTimeBetweenOrderByInterviewTimeAsc(
                candidateId, filter.start(), filter.end());
        } else if (filter.status() != null) {
            entities = repository.findByCandidateIdAndStatusOrderByInterviewTimeAsc(
                candidateId, InterviewStatus.valueOf(filter.status()));
        } else {
            entities = repository.findAllByCandidateIdOrderByInterviewTimeAsc(candidateId);
        }

        return entities.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    public InterviewScheduleDTO getById(UUID candidateId, Long id) {
        return toDTO(getByIdOrThrow(candidateId, id));
    }

    private InterviewScheduleEntity getByIdOrThrow(UUID candidateId, Long id) {
        return repository.findByIdAndCandidateId(id, candidateId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SCHEDULE_NOT_FOUND, "面试日程不存在: " + id));
    }

    private InterviewScheduleDTO toDTO(InterviewScheduleEntity entity) {
        InterviewScheduleDTO dto = new InterviewScheduleDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
