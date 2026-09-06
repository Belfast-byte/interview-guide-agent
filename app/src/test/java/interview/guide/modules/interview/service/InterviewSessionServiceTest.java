package interview.guide.modules.interview.service;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.listener.EvaluateStreamProducer;
import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewReportDTO;
import interview.guide.modules.interview.model.InterviewSessionDTO;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.model.SubmitAnswerRequest;
import interview.guide.modules.resume.model.ResumeEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class InterviewSessionServiceTest {

    @Mock private InterviewQuestionService questions;
    @Mock private AnswerEvaluationService evaluation;
    @Mock private InterviewPersistenceService persistence;
    @Mock private EvaluateStreamProducer producer;
    @Mock private LlmProviderRegistry providers;
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID candidateId = UUID.randomUUID();

    @Test
    void shouldRestoreSavedAnswersWithoutLosingQuestionMetadata() {
        InterviewSessionEntity entity = session();
        ResumeEntity resume = new ResumeEntity();
        resume.setResumeText("候选人简历");
        entity.setResume(resume);
        entity.setCurrentQuestionIndex(1);
        entity.setStatus(InterviewSessionEntity.SessionStatus.IN_PROGRESS);
        when(persistence.findBySessionId(candidateId, "session-1")).thenReturn(Optional.of(entity));
        when(persistence.findAnswersBySessionId("session-1")).thenReturn(List.of(answer(0, "已保存回答")));

        InterviewSessionDTO result = service().getSession(candidateId, "session-1");

        assertThat(result).isEqualTo(new InterviewSessionDTO(
            "session-1", "候选人简历", 2, 1,
            List.of(question().withAnswer("已保存回答"), secondQuestion()),
            InterviewSessionDTO.SessionStatus.IN_PROGRESS, 9L, "Redis"));
        // 组装用户视图不把答案写回题目快照。
        assertThat(mapper.readTree(entity.getQuestionsJson()).get(0).get("userAnswer").asString())
            .isEqualTo("题目快照中的旧回答");
    }

    @Test
    void shouldRestoreUnfinishedSessionAndCurrentQuestionFromDatabase() {
        InterviewSessionEntity entity = session();
        when(persistence.findUnfinishedSession(candidateId, 3L)).thenReturn(Optional.of(entity));
        when(persistence.findBySessionId("session-1")).thenReturn(Optional.of(entity));
        when(persistence.findAnswersBySessionId("session-1")).thenReturn(List.of(answer(0, "暂存回答")));

        assertThat(service().findUnfinishedSession(candidateId, 3L).orElseThrow().questions().getFirst())
            .isEqualTo(question().withAnswer("暂存回答"));
        assertThat(service().getCurrentQuestion("session-1"))
            .isEqualTo(question().withAnswer("暂存回答"));
    }

    @Test
    void shouldNotReadAnswersWhenOwnedSessionIsMissing() {
        when(persistence.findBySessionId(candidateId, "session-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getSession(candidateId, "session-1"))
            .isInstanceOf(BusinessException.class);
        verifyNoInteractions(questions, evaluation, producer, providers);
        verify(persistence, never()).findAnswersBySessionId(any());
    }

    @Test
    void shouldNotDispatchEvaluationWhenAnswerCommitFails() {
        InterviewSessionEntity entity = session();
        entity.setCurrentQuestionIndex(1);
        when(persistence.findBySessionId("session-1")).thenReturn(Optional.of(entity));
        when(persistence.findAnswersBySessionId("session-1")).thenReturn(List.of());
        BusinessException conflict = new BusinessException(ErrorCode.INTERVIEW_ANSWER_SAVE_FAILED);
        doThrow(conflict).when(persistence).persistAnswer("session-1", 1, "最后一题回答", true);

        assertThatThrownBy(() -> service().submitAnswer(new SubmitAnswerRequest(
            "session-1", 1, "最后一题回答"))).isSameAs(conflict);
        verifyNoInteractions(producer);
    }

    @Test
    void shouldDispatchFinalAnswerOnlyAfterPersistenceSucceeds() {
        InterviewSessionEntity entity = session();
        entity.setCurrentQuestionIndex(1);
        when(persistence.findBySessionId("session-1")).thenReturn(Optional.of(entity));
        when(persistence.findAnswersBySessionId("session-1")).thenReturn(List.of());

        var result = service().submitAnswer(new SubmitAnswerRequest("session-1", 1, "最后一题回答"));

        assertThat(result.hasNextQuestion()).isFalse();
        assertThat(result.nextQuestion()).isNull();
        var order = inOrder(persistence, producer);
        order.verify(persistence).persistAnswer("session-1", 1, "最后一题回答", true);
        order.verify(producer).sendEvaluateTask("session-1");
    }

    @Test
    void shouldGenerateReportUsingPersistedAnswersAndProvider() {
        InterviewSessionEntity entity = session();
        entity.setStatus(InterviewSessionEntity.SessionStatus.COMPLETED);
        entity.setLlmProvider("candidate-provider");
        when(persistence.findBySessionId("session-1")).thenReturn(Optional.of(entity));
        when(persistence.findAnswersBySessionId("session-1")).thenReturn(List.of(answer(0, "已保存回答")));
        ChatClient client = mock(ChatClient.class);
        when(providers.getChatClientOrDefault("candidate-provider")).thenReturn(client);
        InterviewReportDTO report = new InterviewReportDTO(
            "session-1", 2, 70, List.of(), List.of(), "评价", List.of(), List.of(), List.of());
        when(evaluation.evaluateInterview(eq(client), eq("session-1"), eq(""),
            eq(List.of(question().withAnswer("已保存回答"), secondQuestion())))).thenReturn(report);

        assertThat(service().generateReport("session-1")).isSameAs(report);
        verify(persistence).saveReport("session-1", report);
    }

    private InterviewSessionService service() {
        return new InterviewSessionService(questions, evaluation, persistence, mapper, producer, providers);
    }

    private InterviewSessionEntity session() {
        InterviewSessionEntity entity = new InterviewSessionEntity();
        entity.setSessionId("session-1");
        entity.setCandidateId(candidateId);
        entity.setQuestionsJson(mapper.writeValueAsString(List.of(question(), secondQuestion())));
        entity.setKnowledgeBaseId(9L);
        entity.setInterviewCategory("Redis");
        return entity;
    }

    private InterviewQuestionDTO question() {
        return new InterviewQuestionDTO(0, "解释持久化", "REDIS", "Redis", "RDB 与 AOF",
            "题目快照中的旧回答", 60, "原反馈", true, 1, "参考答案", List.of("原子性"), "量规", "来源");
    }

    private InterviewQuestionDTO secondQuestion() {
        return InterviewQuestionDTO.create(1, "解释缓存一致性", "REDIS", "Redis");
    }

    private InterviewAnswerEntity answer(int index, String text) {
        InterviewAnswerEntity answer = new InterviewAnswerEntity();
        answer.setQuestionIndex(index);
        answer.setUserAnswer(text);
        return answer;
    }
}
