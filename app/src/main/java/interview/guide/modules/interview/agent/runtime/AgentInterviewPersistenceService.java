package interview.guide.modules.interview.agent.runtime;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.model.AgentInterviewSessionEntity;
import interview.guide.modules.interview.agent.repository.AgentInterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Agent 面试持久化服务，负责会话、轮次和状态的读写与状态流转。
 */
@Service
@RequiredArgsConstructor
public class AgentInterviewPersistenceService {

  private final AgentInterviewSessionRepository sessionRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public AgentLoopState create(String jd, String resume, int maxTurns) {
    AgentInterviewSessionEntity entity = new AgentInterviewSessionEntity();
    entity.setSessionId(UUID.randomUUID().toString());
    entity.setRuntimeVersion(InterviewAgentLoop.RUNTIME_VERSION);
    entity.setJd(jd);
    entity.setResume(resume);
    entity.setCurrentTurn(0);
    entity.setMaxTurns(maxTurns);
    entity.setTurnsJson("[]");
    entity.setStatus(AgentLoopStatus.CREATED);
    return toState(sessionRepository.save(entity));
  }

  @Transactional(readOnly = true)
  public AgentLoopState get(String sessionId) {
    return toState(find(sessionId));
  }

  @Transactional
  public void freezeSkill(String sessionId, LoadedSkill skill) {
    AgentInterviewSessionEntity entity = find(sessionId);
    if (entity.getSelectedSkillId() != null) {
      if (!entity.getSelectedSkillHash().equals(skill.hash())) {
        throw decisionFailed("会话已冻结其他 Skill，不能再次选择");
      }
      return;
    }
    entity.setSelectedSkillId(skill.id());
    entity.setSelectedSkillName(skill.name());
    entity.setSelectedSkillDescription(skill.description());
    entity.setSelectedSkillBody(skill.body());
    entity.setSelectedSkillHash(skill.hash());
    sessionRepository.save(entity);
  }

  @Transactional
  public void saveInitialQuestion(String sessionId, String question) {
    AgentInterviewSessionEntity entity = find(sessionId);
    ensureActive(entity);
    if (entity.getSelectedSkillId() == null) {
      throw decisionFailed("生成第一题前必须先加载 Skill");
    }
    if (entity.getCurrentTurn() != 0) {
      throw decisionFailed("会话已经生成第一题");
    }
    writeTurns(entity, List.of(new Turn(1, question, null)));
    entity.setCurrentTurn(1);
    entity.setStatus(AgentLoopStatus.IN_PROGRESS);
    sessionRepository.save(entity);
  }

  @Transactional
  public void saveAnswerAndQuestion(
      String sessionId,
      int expectedTurn,
      String answer,
      AnswerDepthLevel assessment,
      AnswerEvidence evidence,
      String nextQuestion
  ) {
    AgentInterviewSessionEntity entity = find(sessionId);
    ensureExpectedActiveTurn(entity, expectedTurn);
    List<Turn> turns = new ArrayList<>(readTurns(entity));
    answerCurrentTurn(turns, expectedTurn, answer, assessment, evidence);
    turns.add(new Turn(expectedTurn + 1, nextQuestion, null));
    writeTurns(entity, turns);
    entity.setCurrentTurn(expectedTurn + 1);
    sessionRepository.save(entity);
  }

  @Transactional
  public void saveAnswerAndFinish(
      String sessionId,
      int expectedTurn,
      String answer,
      AnswerDepthLevel assessment,
      AnswerEvidence evidence,
      String reason
  ) {
    AgentInterviewSessionEntity entity = find(sessionId);
    ensureExpectedActiveTurn(entity, expectedTurn);
    List<Turn> turns = new ArrayList<>(readTurns(entity));
    answerCurrentTurn(turns, expectedTurn, answer, assessment, evidence);
    writeTurns(entity, turns);
    complete(entity, reason);
    sessionRepository.save(entity);
  }

  @Transactional
  public void finishBeforeFirstQuestion(String sessionId, String reason) {
    AgentInterviewSessionEntity entity = find(sessionId);
    ensureActive(entity);
    if (entity.getCurrentTurn() != 0) {
      throw decisionFailed("只能在首题生成前直接结束会话");
    }
    complete(entity, reason);
    sessionRepository.save(entity);
  }

  @Transactional
  public void markFailed(String sessionId, String reason) {
    AgentInterviewSessionEntity entity = find(sessionId);
    if (entity.getStatus() == AgentLoopStatus.COMPLETED) {
      return;
    }
    entity.setStatus(AgentLoopStatus.FAILED);
    entity.setFinishReason(normalizeReason(reason));
    entity.setCompletedAt(LocalDateTime.now());
    sessionRepository.save(entity);
  }

  private void complete(AgentInterviewSessionEntity entity, String reason) {
    entity.setStatus(AgentLoopStatus.COMPLETED);
    entity.setFinishReason(normalizeReason(reason));
    entity.setCompletedAt(LocalDateTime.now());
  }

  private void ensureExpectedActiveTurn(AgentInterviewSessionEntity entity, int expectedTurn) {
    ensureActive(entity);
    if (entity.getCurrentTurn() != expectedTurn || expectedTurn <= 0) {
      throw decisionFailed("会话轮次已变化，请刷新后重试");
    }
    List<Turn> turns = readTurns(entity);
    if (turns.isEmpty() || turns.getLast().answer() != null) {
      throw decisionFailed("当前问题已经回答");
    }
  }

  private void ensureActive(AgentInterviewSessionEntity entity) {
    if (entity.getStatus() == AgentLoopStatus.COMPLETED) {
      throw new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED);
    }
    if (entity.getStatus() == AgentLoopStatus.FAILED) {
      throw decisionFailed("Agent 面试会话已失败");
    }
  }

  private void answerCurrentTurn(
      List<Turn> turns,
      int expectedTurn,
      String answer,
      AnswerDepthLevel assessment,
      AnswerEvidence evidence
  ) {
    Turn current = turns.getLast();
    turns.set(turns.size() - 1, new Turn(
        expectedTurn,
        current.question(),
        answer,
        assessment,
        evidence
    ));
  }

  private AgentInterviewSessionEntity find(String sessionId) {
    return sessionRepository.findBySessionId(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
  }

  private AgentLoopState toState(AgentInterviewSessionEntity entity) {
    LoadedSkill loadedSkill = entity.getSelectedSkillId() == null
        ? null
        : new LoadedSkill(
            entity.getSelectedSkillId(),
            entity.getSelectedSkillName(),
            entity.getSelectedSkillDescription(),
            entity.getSelectedSkillBody(),
            entity.getSelectedSkillHash()
        );
    return new AgentLoopState(
        entity.getSessionId(),
        entity.getRuntimeVersion(),
        entity.getJd(),
        entity.getResume(),
        entity.getCurrentTurn(),
        entity.getMaxTurns(),
        loadedSkill,
        readTurns(entity),
        entity.getStatus(),
        entity.getFinishReason()
    );
  }

  private List<Turn> readTurns(AgentInterviewSessionEntity entity) {
    try {
      return objectMapper.readValue(entity.getTurnsJson(), new TypeReference<>() {});
    } catch (JacksonException e) {
      throw new BusinessException(
          ErrorCode.INTERNAL_ERROR,
          "Agent 面试轮次快照损坏",
          e
      );
    }
  }

  private void writeTurns(AgentInterviewSessionEntity entity, List<Turn> turns) {
    try {
      entity.setTurnsJson(objectMapper.writeValueAsString(turns));
    } catch (JacksonException e) {
      throw new BusinessException(
          ErrorCode.INTERNAL_ERROR,
          "Agent 面试轮次快照保存失败",
          e
      );
    }
  }

  private String normalizeReason(String reason) {
    if (reason == null || reason.isBlank()) {
      return "Agent 主动结束面试";
    }
    return reason.length() <= 500 ? reason : reason.substring(0, 500);
  }

  private BusinessException decisionFailed(String message) {
    return new BusinessException(ErrorCode.AGENT_INTERVIEW_DECISION_FAILED, message);
  }
}
