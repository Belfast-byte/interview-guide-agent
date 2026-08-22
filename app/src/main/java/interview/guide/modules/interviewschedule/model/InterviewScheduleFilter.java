package interview.guide.modules.interviewschedule.model;

import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

public record InterviewScheduleFilter(
    String status,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
) {
}
