package interview.guide.modules.interview.agent.runtime;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.model.AgentInterviewSessionEntity;
import interview.guide.modules.interview.agent.repository.AgentInterviewSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentInterviewPersistenceServiceTest {

  @Mock
  private AgentInterviewSessionRepository sessionRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("保存追问时在同一快照中写入当前回答和下一题")
  void shouldPersistAnswerAndNextQuestionTogether() throws Exception {
    AgentInterviewSessionEntity entity = activeEntity();
    when(sessionRepository.findBySessionId("sid")).thenReturn(Optional.of(entity));
    when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    AgentInterviewPersistenceService service = service();

    AnswerEvidence evidence = new AnswerEvidence("解释了具体做法", "当前回答");
    service.saveAnswerAndQuestion(
        "sid",
        1,
        "当前回答",
        AnswerDepthLevel.L2,
        evidence,
        "自适应追问？"
    );

    ArgumentCaptor<AgentInterviewSessionEntity> captor =
        ArgumentCaptor.forClass(AgentInterviewSessionEntity.class);
    verify(sessionRepository).save(captor.capture());
    AgentInterviewSessionEntity saved = captor.getValue();
    Turn[] turns = objectMapper.readValue(saved.getTurnsJson(), Turn[].class);
    assertThat(turns).containsExactly(
        new Turn(1, "原问题？", "当前回答", AnswerDepthLevel.L2, evidence),
        new Turn(2, "自适应追问？", null)
    );
    assertThat(saved.getCurrentTurn()).isEqualTo(2);
  }

  @Test
  @DisplayName("旧版轮次 JSON 缺少评估字段时仍可读取")
  void shouldReadMvpV1TurnsWithoutAssessmentFields() {
    AgentInterviewSessionEntity entity = activeEntity();
    entity.setRuntimeVersion("agent-loop-mvp-v1");
    entity.setTurnsJson(
        "[{\"turnNumber\":1,\"question\":\"原问题？\",\"answer\":\"旧回答\"}]"
    );
    when(sessionRepository.findBySessionId("sid")).thenReturn(Optional.of(entity));

    AgentLoopState state = service().get("sid");

    assertThat(state.runtimeVersion()).isEqualTo("agent-loop-mvp-v1");
    assertThat(state.turns()).containsExactly(new Turn(1, "原问题？", "旧回答", null, null));
  }

  @Test
  @DisplayName("Skill 一旦冻结，不能替换为不同 hash 的内容")
  void shouldRejectChangingFrozenSkill() {
    AgentInterviewSessionEntity entity = activeEntity();
    entity.setSelectedSkillId("java-backend");
    entity.setSelectedSkillHash("old-hash");
    when(sessionRepository.findBySessionId("sid")).thenReturn(Optional.of(entity));
    AgentInterviewPersistenceService service = service();

    assertThatThrownBy(() -> service.freezeSkill("sid", new LoadedSkill(
        "java-backend",
        "Java 后端",
        "描述",
        "变化后的内容",
        "new-hash"
    ))).isInstanceOf(BusinessException.class)
        .hasMessageContaining("冻结");
  }

  @Test
  @DisplayName("候选人读取会话时把归属条件下推到仓储")
  void shouldQuerySessionWithCandidateOwnership() {
    UUID candidateId = UUID.randomUUID();
    when(sessionRepository.findBySessionIdAndCandidateId("sid", candidateId))
        .thenReturn(Optional.of(activeEntity()));

    service().get(candidateId, "sid");

    verify(sessionRepository).findBySessionIdAndCandidateId("sid", candidateId);
  }

  private AgentInterviewPersistenceService service() {
    return new AgentInterviewPersistenceService(sessionRepository, objectMapper);
  }

  private AgentInterviewSessionEntity activeEntity() {
    AgentInterviewSessionEntity entity = new AgentInterviewSessionEntity();
    entity.setSessionId("sid");
    entity.setRuntimeVersion(InterviewAgentLoop.RUNTIME_VERSION);
    entity.setJd("JD");
    entity.setResume("Resume");
    entity.setCurrentTurn(1);
    entity.setMaxTurns(6);
    entity.setTurnsJson("[{\"turnNumber\":1,\"question\":\"原问题？\",\"answer\":null}]");
    entity.setStatus(AgentLoopStatus.IN_PROGRESS);
    return entity;
  }
}
