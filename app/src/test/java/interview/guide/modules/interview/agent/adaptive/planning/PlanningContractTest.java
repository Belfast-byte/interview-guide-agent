package interview.guide.modules.interview.agent.adaptive.planning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanningContractTest {

  @Test
  @DisplayName("规划请求只携带当前会话的 JD 和简历")
  void shouldKeepPlanningContextIndependentFromAssessment() {
    assertThat(Arrays.stream(PlanningRequest.class.getRecordComponents())
        .map(component -> component.getName()))
        .containsExactly("sessionId", "jd", "resume");
  }

  @Test
  @DisplayName("规划建议保持模型给出的维度顺序且不可被调用方改写")
  void shouldKeepOrderedImmutableDimensions() {
    List<DimensionProposal> dimensions = new ArrayList<>(List.of(
        dimension("专业基础", "缓存与并发"),
        dimension("项目经验", "架构取舍")
    ));

    PlanProposal proposal = new PlanProposal(dimensions);
    dimensions.clear();

    assertThat(proposal.dimensions())
        .extracting(DimensionProposal::dimension)
        .containsExactly("专业基础", "项目经验");
    assertThatThrownBy(() -> proposal.dimensions().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("维度建议中的工具名单不可被调用方改写")
  void shouldKeepSuggestedToolsImmutable() {
    List<String> tools = new ArrayList<>(List.of("question_bank_search"));
    DimensionProposal dimension = new DimensionProposal(
        "专业基础",
        "缓存与并发",
        2,
        tools,
        "backend-interviewer"
    );
    tools.clear();

    assertThat(dimension.suggestedTools()).containsExactly("question_bank_search");
    assertThatThrownBy(() -> dimension.suggestedTools().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private DimensionProposal dimension(String name, String focus) {
    return new DimensionProposal(name, focus, 2, List.of(), null);
  }
}
