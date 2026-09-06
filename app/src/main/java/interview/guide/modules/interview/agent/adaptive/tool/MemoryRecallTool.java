package interview.guide.modules.interview.agent.adaptive.tool;

import org.springframework.ai.tokenizer.TokenCountEstimator;
import tools.jackson.databind.ObjectMapper;
import interview.guide.modules.interview.agent.adaptive.memory.observation.MemoryEvidenceSource;
import interview.guide.modules.interview.agent.adaptive.memory.observation.CapabilityBelief;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation.AdoptableSource;
import java.util.*;
import org.springframework.stereotype.Component;

/** 按用途召回当前用户记忆；沿用工具网关，不接受模型指定 owner。 */
@Component
public class MemoryRecallTool implements ReadOnlyAgentTool {
  private final MemoryEvidenceSource memory;
  private final TokenCountEstimator estimator;
  private static final ObjectMapper JSON=new ObjectMapper();
  private static final int MAX_TOKENS=1800;
  public MemoryRecallTool(MemoryEvidenceSource memory, TokenCountEstimator estimator) {
    this.memory=memory; this.estimator=estimator;
  }
  public String name() { return "memory_recall"; }
  public void validate(ReadToolRequest request) {
    if(!Set.of("purpose","targetId").containsAll(request.arguments().keySet()))
      throw new ReadToolValidationException("arguments","只支持 purpose 和 targetId");
    if(!Set.of("AVOID_REPEAT","CONTINUE_LEARNING","VERIFY_INDEPENDENCE").contains(request.arguments().get("purpose")))
      throw new ReadToolValidationException("arguments.purpose","不支持的记忆用途");
    if(request.context().facts().coverage().targets().stream().noneMatch(t ->
        t.targetId().equals(request.arguments().get("targetId"))))
      throw new ReadToolValidationException("arguments.targetId","目标不属于当前计划");
  }
  public ReadToolResult execute(ReadToolRequest request) {
    var owner=request.context().session().identity().owner();
    var target=request.context().facts().coverage().targets().stream()
        .filter(t -> t.targetId().equals(request.arguments().get("targetId"))).findFirst().orElseThrow();
    var topic=target.target().identity().topic();
    try {
      if("AVOID_REPEAT".equals(request.arguments().get("purpose"))) {
        var recent=new ArrayList<>(memory.recentQuestions(owner,topic,5)
            .stream().map(q -> clip(q,1000)).toList());
        while(!recent.isEmpty() && estimator.estimate(JSON.writeValueAsString(recent))>MAX_TOKENS-100)
          recent.removeLast();
        return new ReadToolResult.Success(Map.of("recentQuestions",recent,"purpose","AVOID_REPEAT",
            "complete",false),List.of());
      }
      var all=memory.beliefs(owner,topic);
      var candidates=all.stream().filter(b -> !"VERIFY_INDEPENDENCE".equals(request.arguments().get("purpose"))
          || b.needsVerification()).sorted(Comparator.comparing(CapabilityBelief::needsVerification).reversed()
              .thenComparing(CapabilityBelief::updatedAt,Comparator.reverseOrder())).toList();
      var selected=new ArrayList<>(candidates.stream().limit(5).toList());
      if(selected.isEmpty()) return new ReadToolResult.Empty("没有可用的证据记忆；继续按当前事实出题");
      var hits=new ArrayList<>(selected.stream().map(b -> Map.<String,Object>of(
          "reference",MemoryEvidenceSource.reference(b),"capabilityKey",b.capabilityKey(),
          "objective",clip(b.objective(),500),"state",b.state(),"needsVerification",b.needsVerification(),
          "independentOpportunities",b.independentOpportunities(),"revision",b.revision(),
          "latestObservation",clip(b.latestObservation(),700),"allowedUse","仅供出题规划，不用于当前评分",
          "verificationRequirement","同能力、不同场景、无提示，回答必须提供机制证据" )).toList());
      while(!hits.isEmpty() && estimator.estimate(JSON.writeValueAsString(hits))>MAX_TOKENS-100) {
        hits.removeLast(); selected.removeLast();
      }
      if(hits.isEmpty()) return new ReadToolResult.Empty("记忆超出检索预算；继续按当前事实出题");
      return new ReadToolResult.Success(Map.of("memories",hits,"complete",selected.size()==candidates.size()),
          selected.stream().map(b -> new AdoptableSource(MemoryEvidenceSource.reference(b),"memory",
              b.capabilityKey(),b.revision())).toList());
    } catch(RuntimeException error) {
      return new ReadToolResult.Error("记忆暂不可用，继续按当前事实出题");
    }
  }
  private String clip(String s,int max) { return s.length()<=max ? s : s.substring(0,max)+"…"; }
}
