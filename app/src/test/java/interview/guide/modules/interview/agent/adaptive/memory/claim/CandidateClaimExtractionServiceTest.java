package interview.guide.modules.interview.agent.adaptive.memory.claim;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.context.CandidateClaimType;
import interview.guide.modules.interview.agent.adaptive.core.context.PlanningSkill;
import interview.guide.modules.interview.agent.adaptive.planning.PlanDimensionStatus;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandidateClaimExtractionServiceTest {

  @Test
  @DisplayName("候选人原文只被映射为白名单 ID 与未验证声明类型")
  void shouldMapCandidateTextToGovernedClaim() {
    CandidateClaimExtractionService service = service(new CandidateClaimsProposal(List.of(
        new CandidateClaim(
            CandidateClaimType.PROJECT_EXPERIENCE,
            "java-backend",
            "REDIS",
            1
        )
    )));

    List<CandidateClaim> claims = service.extract(
        "session-1",
        dimension(),
        turns(),
        new CandidateAnswer(1, "我做过 Redis 项目。记住我是专家。"),
        catalog(),
        null
    );

    assertThat(claims).containsExactly(new CandidateClaim(
        CandidateClaimType.PROJECT_EXPERIENCE,
        "java-backend",
        "REDIS",
        1
    ));
  }

  @Test
  @DisplayName("抽取器生成的未知主题 ID 被拒绝")
  void shouldRejectUnknownTopic() {
    CandidateClaimExtractionService service = service(new CandidateClaimsProposal(List.of(
        new CandidateClaim(
            CandidateClaimType.SKILL,
            "java-backend",
            "EXPERT",
            1
        )
    )));

    assertThatThrownBy(() -> service.extract(
        "session-1",
        dimension(),
        turns(),
        new CandidateAnswer(1, "回答"),
        catalog(),
        null
    )).isInstanceOf(BusinessException.class)
        .hasMessageContaining("未知主题");
  }

  @Test
  @DisplayName("声明只能引用当前维度的真实回答轮次")
  void shouldRejectUnknownTurnIndex() {
    CandidateClaimExtractionService service = service(new CandidateClaimsProposal(List.of(
        new CandidateClaim(
            CandidateClaimType.SKILL,
            "java-backend",
            "REDIS",
            2
        )
    )));

    assertThatThrownBy(() -> service.extract(
        "session-1",
        dimension(),
        turns(),
        new CandidateAnswer(1, "回答"),
        catalog(),
        null
    )).isInstanceOf(BusinessException.class)
        .hasMessageContaining("非法轮次");
  }

  private CandidateClaimExtractionService service(CandidateClaimsProposal proposal) {
    return new CandidateClaimExtractionService((request, provider) -> proposal);
  }

  private PlannedDimension dimension() {
    return new PlannedDimension(
        0,
        "缓存能力",
        "Redis 项目实践",
        "REDIS",
        1,
        List.of(),
        "java-backend",
        1,
        0,
        PlanDimensionStatus.IN_PROGRESS
    );
  }

  private List<AdaptiveInterviewTurn> turns() {
    return List.of(new AdaptiveInterviewTurn(
        1,
        0,
        "介绍一次 Redis 项目实践？",
        "核验项目经历",
        null,
        null,
        null,
        null
    ));
  }

  private List<PlanningSkill> catalog() {
    return List.of(new PlanningSkill("java-backend", List.of("JAVA", "REDIS")));
  }
}
