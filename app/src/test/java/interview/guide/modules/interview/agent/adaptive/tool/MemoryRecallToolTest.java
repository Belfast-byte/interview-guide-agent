package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.core.context.*;
import interview.guide.modules.interview.agent.adaptive.memory.observation.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemoryRecallToolTest {
  final MemoryEvidenceSource source=mock(MemoryEvidenceSource.class);
  final TokenCountEstimator tokens=mock(TokenCountEstimator.class);
  final MemoryRecallTool tool=new MemoryRecallTool(source,tokens);
  final MemoryOwner owner=new MemoryOwner("tenant","candidate");
  final TopicKey topic=new TopicKey("java","concurrency");

  @Test void verifiesOnlyCurrentOwnersUnresolvedCapabilities() {
    when(source.beliefs(owner,topic)).thenReturn(List.of(belief("good",false),belief("pending",true)));
    var request=request(Map.of("purpose","VERIFY_INDEPENDENCE","targetId","target-0"));
    tool.validate(request);
    var result=(ReadToolResult.Success)tool.execute(request);
    assertThat(result.adoptableSources()).singleElement()
        .satisfies(s -> assertThat(s.id()).isEqualTo("pending"));
    verify(source).beliefs(owner,topic);
  }

  @Test void rejectsModelSuppliedOwnerAndUnknownTarget() {
    assertThatThrownBy(() -> tool.validate(request(Map.of("purpose","VERIFY_INDEPENDENCE",
        "targetId","target-0","owner","other")))).isInstanceOf(ReadToolValidationException.class);
    assertThatThrownBy(() -> tool.validate(request(Map.of("purpose","VERIFY_INDEPENDENCE",
        "targetId","other")))).isInstanceOf(ReadToolValidationException.class);
    verifyNoInteractions(source);
  }

  @Test void dropsSourcesTogetherWithOverBudgetHits() {
    when(source.beliefs(owner,topic)).thenReturn(List.of(belief("a",true),belief("b",true)));
    when(tokens.estimate(anyString())).thenAnswer(invocation ->
        invocation.<String>getArgument(0).contains("\"capabilityKey\":\"b\"") ? 2000 : 600);
    var result=(ReadToolResult.Success)tool.execute(request(Map.of("purpose","CONTINUE_LEARNING","targetId","target-0")));
    assertThat(result.adoptableSources()).hasSize(1);
    assertThat((List<?>)result.data().get("memories")).hasSize(1);
    assertThat(result.data().get("complete")).isEqualTo(false);
  }

  @Test void repetitionRecallDoesNotExposeAbilityLabelsAndFailuresAllowContinuation() {
    when(source.recentQuestions(owner,topic,5)).thenReturn(List.of("原题"));
    var result=(ReadToolResult.Success)tool.execute(request(Map.of("purpose","AVOID_REPEAT","targetId","target-0")));
    assertThat(result.adoptableSources()).isEmpty();
    verify(source,never()).beliefs(any(),any());
    when(source.beliefs(owner,topic)).thenThrow(new IllegalStateException("offline"));
    assertThat(tool.execute(request(Map.of("purpose","CONTINUE_LEARNING","targetId","target-0"))))
        .isInstanceOf(ReadToolResult.Error.class);
  }

  private CapabilityBelief belief(String key,boolean pending) {
    return new CapabilityBelief(key,"复合操作原子性",pending?"NEEDS_REVALIDATION":"INDEPENDENTLY_DEMONSTRATED",
        pending,1,"v1",List.of(1L),"有具体原文支持的观察","2026-09-05");
  }
  private ReadToolRequest request(Map<String,Object> args) {
    var context=mock(AgentContext.class,RETURNS_DEEP_STUBS);
    var target=mock(CoverageView.TargetCoverage.class,RETURNS_DEEP_STUBS);
    when(context.session().identity().owner()).thenReturn(owner);
    when(context.facts().coverage().targets()).thenReturn(List.of(target));
    when(target.targetId()).thenReturn("target-0");
    when(target.target().identity().topic()).thenReturn(topic);
    return new ReadToolRequest(context,args,Long.MAX_VALUE);
  }
}
