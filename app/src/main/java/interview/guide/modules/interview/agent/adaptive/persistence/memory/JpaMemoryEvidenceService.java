package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.memory.observation.*;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.util.Sha256;
import interview.guide.modules.interview.agent.adaptive.core.context.*;
import interview.guide.modules.interview.agent.adaptive.memory.episode.*;
import interview.guide.modules.interview.agent.adaptive.persistence.session.*;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanRepository;
import interview.guide.modules.interview.skill.InterviewSkillService;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.ObjectMapper;
import static interview.guide.modules.interview.agent.adaptive.memory.observation.MemoryObservationProposal.*;

/** 复用 Episode 的观察版本写入与按主题读取；不参与当前回答评分。 */
@Service
public class JpaMemoryEvidenceService implements MemoryEvidenceSource {
  public static final String RULES_VERSION="memory-evidence-v1";
  private static final ObjectMapper JSON=new ObjectMapper();
  private final MemoryObservationRepository observations;
  private final EpisodeFactRepository episodes;
  private final AdaptiveAgentTurnRepository turns;
  private final AdaptiveAgentPlanRepository plans;
  private final QuestionExposureRepository exposures;
  private final org.springframework.beans.factory.ObjectProvider<InterviewSkillService> skills;

  public JpaMemoryEvidenceService(MemoryObservationRepository observations, EpisodeFactRepository episodes,
      AdaptiveAgentTurnRepository turns, AdaptiveAgentPlanRepository plans, QuestionExposureRepository exposures,
      org.springframework.beans.factory.ObjectProvider<InterviewSkillService> skills) {
    this.observations=observations; this.episodes=episodes; this.turns=turns; this.plans=plans; this.skills=skills; this.exposures=exposures;
  }

  @Transactional(readOnly=true)
  public MemoryObservationContext context(EpisodeFact episode, AdaptiveAgentTurnEntity turn) {
    var objectives=plans.findBySessionIdOrderByDimensionOrder(episode.sessionId()).stream()
        .filter(p -> p.dimensionOrder()==turn.dimensionOrder()).flatMap(p -> p.toDomain().evidenceObjectives().stream())
        .map(CapabilityTarget.EvidenceObjective::description).distinct().toList();
    // 没有已知考察目标的旧数据只能生成摘要，不能臆造能力目录。
    var all=current(episode.owner(),episode.topic()).stream()
        .filter(r -> r.episodeId<episode.id()).toList();
    var selected=all.subList(Math.max(0,all.size()-20),all.size());
    var prior=selected.stream().map(r -> {
      var e=episodes.findById(r.episodeId).orElseThrow().toDomain();
      var t=turns.findBySessionIdAndTurnIndex(e.sessionId(),e.turnIndex()).orElseThrow();
      return new MemoryObservationContext.PriorObservation(r.id,r.sessionId,r.capabilityKey,
          r.objective,t.question(),r.summary,proposal(r));
    }).toList();
    var sessionTurns=turns.findBySessionIdOrderByTurnIndex(episode.sessionId()).stream()
        .filter(t -> t.turnIndex()<episode.turnIndex())
        .map(t -> new MemoryObservationContext.PriorTurn(t.turnIndex(),t.question(),t.answer())).toList();
    var skillService=skills.getIfAvailable();
    String skillReference=skillService==null ? "" : skillService.buildEvaluationReferenceSection(episode.topic().skillId());
    var exposed=exposures.findPrior(episode.owner(),episode.topic(),episode.createdAt(),episode.turnId(),PageRequest.of(0,51)).stream()
        .map(e -> e.toDomain()).toList();
    return new MemoryObservationContext(objectives,skillReference,turn.toDomain().adoptedRubrics(),
        sessionTurns,prior,selected.size()==all.size() && exposed.size()<50,
        turn.triggerType()==interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType.ASSESSMENT_GAP, exposed.stream().limit(50).map(e -> e.questionText()).toList());
  }

  public static String fingerprint(EpisodeEnrichmentRequest input) {
    return Sha256.hex(JSON.writeValueAsString(input));
  }

  /** 调用者持有 Episode 行锁，与摘要、标签一次提交。 */
  public void append(EpisodeFactEntity entity, EpisodeEnrichmentCompletion completion) {
    var input=completion.input();
    if(input==null || input.memory()==null || input.memory().objectives().isEmpty()) return;
    var p=completion.observation();
    validate(p,input);
    var episode=entity.toDomain();
    if(input.episodeId()!=episode.id() || !input.sessionId().equals(episode.sessionId())
        || input.turnIndex()!=episode.turnIndex() || !input.topic().equals(episode.topic()))
      throw invalid("观察输入不属于当前 Episode");
    if(completion.answerSummary()==null || completion.answerSummary().isBlank()
        || completion.answerSummary().length()>1500) throw invalid("记忆摘要长度不合法");
    var history=observations.findByEpisodeIdOrderByRevisionDesc(episode.id());
    if(!history.isEmpty() && history.getFirst().retracted) return; // 撤销后不可被迟到任务恢复。
    var previous=p.relatedRevisionId()==null ? null : requireCurrent(episode.owner(),p.relatedRevisionId());
    if(previous!=null && (!previous.skillId.equals(episode.topic().skillId())
        || !previous.focusId.equals(episode.topic().focusId()))) throw invalid("跨主题观察比较");
    var row=new MemoryObservationRevision();
    row.episodeId=episode.id(); row.revision=history.isEmpty()?1:history.getFirst().revision+1;
    row.tenantId=episode.owner().tenantId(); row.candidateId=episode.owner().candidateId();
    row.sessionId=episode.sessionId(); row.sessionMode=episode.sessionMode().name();
    row.skillId=episode.topic().skillId(); row.focusId=episode.topic().focusId();
    row.objective=input.memory().objectives().get(p.objectiveIndex());
    row.capabilityKey=previous!=null && p.sameCapability() && p.relation()!=Relation.INCOMPARABLE
        ? previous.capabilityKey : Sha256.hex(row.skillId+"\n"+row.focusId+"\n"+row.objective
            +(p.relation()==Relation.INCOMPARABLE ? "\n"+episode.id() : ""));
    // 同 session、同能力只算一次机会；不根据 parentTurnIndex 猜测独立性。
    row.opportunityKey=Sha256.hex(episode.sessionId()+"\n"+row.capabilityKey);
    row.independent=p.assistance()==Assistance.NONE && !p.answerExposed()
        && input.memory().priorQuestions().stream().noneMatch(q ->
            QuestionFingerprint.wording(q).equals(QuestionFingerprint.wording(input.question())))
        && (previous!=null || input.memory().priorObservations().stream()
            .noneMatch(r -> r.capabilityKey().equals(row.capabilityKey)))
        && !input.memory().followUp() && input.memory().priorObservationsComplete()
        && (previous==null || (!previous.sessionId.equals(row.sessionId)
            && p.sameCapability() && p.differentScenario()
            && (p.relation()==Relation.SUPPORT || p.relation()==Relation.CONTRADICTION)))
        && input.memory().priorObservations().stream().noneMatch(r -> r.sessionId().equals(row.sessionId)
            && r.capabilityKey().equals(row.capabilityKey));
    row.inputFingerprint=fingerprint(input); row.rulesVersion=RULES_VERSION;
    row.sourceContextJson=JSON.writeValueAsString(Map.of(
        "skillReferenceHash",Sha256.hex(input.memory().skillReference()),
        "rubricReferences",input.memory().adoptedRubrics().stream().map(r -> r.reference()).toList(),
        "priorRevisionIds",input.memory().priorObservations().stream().map(r -> r.revisionId()).toList(),
        "evidenceIds",input.evidences().stream().map(e -> e.id()).toList(),
        "gapIds",input.probeGaps().stream().map(g -> g.id()).toList(),
        "historyComplete",input.memory().priorObservationsComplete()));
    row.provider=completion.provider(); row.observationJson=JSON.writeValueAsString(p);
    row.summary=completion.answerSummary(); row.createdAt=LocalDateTime.now();
    observations.saveAndFlush(row);
  }

  private void validate(MemoryObservationProposal p, EpisodeEnrichmentRequest input) {
    if(p==null || p.finding()==null || p.assistance()==null || p.relation()==null
        || p.objectiveIndex()<0 || p.objectiveIndex()>=input.memory().objectives().size()
        || p.rationale()==null || p.rationale().isBlank() || p.rationale().length()>1000)
      throw invalid("记忆观察不完整");
    if(p.finding()!=Finding.INSUFFICIENT && !quoted(input.answer(),p.evidenceQuote()))
      throw invalid("观察必须引用当前回答原文");
    if(p.finding()==Finding.INSUFFICIENT && p.evidenceQuote()!=null && !p.evidenceQuote().isBlank()
        && !quoted(input.answer(),p.evidenceQuote())) throw invalid("观察引用不存在");
    if(p.assistance()!=Assistance.NONE && p.assistance()!=Assistance.UNKNOWN) {
      boolean exists=quoted(input.question(),p.assistanceQuote()) || input.memory().sessionTurns().stream()
          .anyMatch(t -> quoted(t.question(),p.assistanceQuote()));
      if(!exists) throw invalid("提示条件必须引用实际面试官消息");
    }
    if((p.relation()==Relation.SUPPORT || p.relation()==Relation.CORRECTION) && p.finding()!=Finding.CORRECT)
      throw invalid("支持或修正关系必须有正确回答证据");
    if(p.relation()==Relation.CONTRADICTION && p.finding()!=Finding.INCORRECT)
      throw invalid("反证关系必须明确指出本次错误");
    if(p.relatedRevisionId()!=null) {
      if(input.memory().priorObservations().stream().noneMatch(r -> r.revisionId()==p.relatedRevisionId()))
        throw invalid("比较对象未出现在记忆输入中");
      if(p.relation()==Relation.NEW) throw invalid("新观察不能同时声明历史比较关系");
      if(p.relation()!=Relation.INCOMPARABLE && !p.sameCapability()) throw invalid("不同能力不能合并判断");
    } else if(p.relation()!=Relation.NEW) throw invalid("观察关系缺少来源版本");
  }

  private static boolean quoted(String text,String quote) {
    return text!=null && quote!=null && !quote.isBlank() && quote.length()<=2000 && text.contains(quote);
  }
  public static MemoryObservationProposal proposal(MemoryObservationRevision r) {
    return JSON.readValue(r.observationJson,MemoryObservationProposal.class);
  }
  private static BusinessException invalid(String message) { return new BusinessException(ErrorCode.AI_SERVICE_ERROR,message); }

  @Transactional(readOnly=true)
  public List<MemoryObservationRevision> current(MemoryOwner owner,TopicKey topic) {
    List<MemoryObservationRevision> all=new ArrayList<>();
    for(int page=0;;page++) {
      var batch=observations.current(owner,topic,PageRequest.of(page,100)); all.addAll(batch);
      if(batch.size()<100) break;
    }
    Set<Long> valid=new HashSet<>(); List<MemoryObservationRevision> result=new ArrayList<>();
    for(var row:all) {
      if(row.retracted) continue;
      var p=proposal(row);
      // 来源被撤销或替代后，依赖旧版本的结论必须重新生成，不能沿用。
      if(p.relatedRevisionId()!=null && !valid.contains(p.relatedRevisionId())) continue;
      valid.add(row.id); result.add(row);
    }
    return List.copyOf(result);
  }

  @Transactional(readOnly=true)
  public MemoryObservationRevision requireCurrent(MemoryOwner owner,long revisionId) {
    var row=observations.findById(revisionId).orElseThrow(() -> invalid("记忆来源不存在"));
    if(!Objects.equals(owner.tenantId(),row.tenantId) || !owner.candidateId().equals(row.candidateId))
      throw invalid("记忆来源不属于当前用户");
    return current(owner,new TopicKey(row.skillId,row.focusId)).stream().filter(r -> r.id==revisionId)
        .findFirst().orElseThrow(() -> invalid("记忆版本已失效"));
  }

  @Transactional(readOnly=true)
  public List<MemoryObservationRevision> audit(MemoryOwner owner,long revisionId) {
    var row=observations.findById(revisionId).orElseThrow(() -> invalid("记忆来源不存在"));
    if(!Objects.equals(row.tenantId,owner.tenantId()) || !row.candidateId.equals(owner.candidateId()))
      throw invalid("记忆来源不属于当前用户");
    return observations.findByEpisodeIdOrderByRevisionDesc(row.episodeId);
  }

  @Transactional
  public void rebuild(MemoryOwner owner,long revisionId) {
    var row=requireCurrent(owner,revisionId);
    var episode=episodes.findLockedById(row.episodeId).orElseThrow();
    requireCurrent(owner,revisionId);
    episode.requestReenrichment();
  }

  @Transactional
  public void retract(MemoryOwner owner,long revisionId,String reason) {
    if(reason==null || reason.isBlank() || reason.length()>500) throw invalid("撤销需要简短理由");
    var old=requireCurrent(owner,revisionId);
    episodes.findLockedById(old.episodeId).orElseThrow();
    old=requireCurrent(owner,revisionId);
    var row=new MemoryObservationRevision();
    row.episodeId=old.episodeId; row.revision=old.revision+1;
    row.tenantId=old.tenantId; row.candidateId=old.candidateId; row.sessionId=old.sessionId;
    row.sessionMode=old.sessionMode; row.skillId=old.skillId; row.focusId=old.focusId;
    row.capabilityKey=old.capabilityKey; row.objective=old.objective; row.opportunityKey=old.opportunityKey;
    row.independent=false; row.retracted=true; row.reason=reason; row.inputFingerprint=old.inputFingerprint;
    row.rulesVersion=RULES_VERSION; row.provider=old.provider; row.observationJson=old.observationJson;
    row.sourceContextJson=old.sourceContextJson; row.summary=old.summary; row.createdAt=LocalDateTime.now(); observations.saveAndFlush(row);
  }

  @Transactional(readOnly=true)
  public List<CapabilityBelief> beliefs(MemoryOwner owner,TopicKey topic) {
    Map<String,List<MemoryObservationRevision>> groups=current(owner,topic).stream()
        .collect(Collectors.groupingBy(r -> r.capabilityKey,LinkedHashMap::new,Collectors.toList()));
    return groups.entrySet().stream().map(e -> project(e.getKey(),e.getValue())).toList();
  }

  public static CapabilityBelief project(String capability,List<MemoryObservationRevision> rows) {
    String state="NO_EVIDENCE"; Set<String> independent=new HashSet<>();
    boolean success=false; boolean needsVerification=false;
    for(var r:rows) {
      var p=proposal(r);
      if(p.finding()==Finding.INSUFFICIENT) continue;
      if(r.independent) independent.add(r.opportunityKey);
      if(p.finding()==Finding.INCORRECT) {
        if(success && r.independent) state="MIXED_EVIDENCE";
        else if(!"MIXED_EVIDENCE".equals(state)) state="NEEDS_REVALIDATION";
        needsVerification=true;
      } else if(r.independent) {
        if(!"MIXED_EVIDENCE".equals(state)) { state="INDEPENDENTLY_DEMONSTRATED"; needsVerification=false; }
        success=true;
      } else if(!success) {
        state=p.assistance()==Assistance.NEUTRAL_PROBE || p.assistance()==Assistance.NONE
            ? "SELF_CORRECTED" : "CORRECTED_WITH_ASSISTANCE";
        if(p.assistance()==Assistance.UNKNOWN || p.relatedRevisionId()==null
            || p.relation()==Relation.INCOMPARABLE) state="NEEDS_REVALIDATION";
        needsVerification=true;
      }
    }
    var last=rows.getLast();
    String revision=Sha256.hex(RULES_VERSION+rows.stream().map(r -> r.id.toString()).collect(Collectors.joining(",")));
    return new CapabilityBelief(capability,last.objective,state,needsVerification,independent.size(),revision,
        rows.stream().map(r -> r.id).toList(),last.summary,last.createdAt.toString());
  }
  public static String reference(CapabilityBelief belief) {
    return "memory:capability:"+belief.capabilityKey()+"@"+belief.revision();
  }

  @Transactional(readOnly=true)
  public List<String> adopt(MemoryOwner owner,TopicKey topic,List<String> references) {
    var selected=references.stream().filter(r -> r.startsWith("memory:")).distinct().toList();
    if(selected.isEmpty()) return List.of();
    var available=beliefs(owner,topic).stream().map(JpaMemoryEvidenceService::reference).toList();
    if(!available.containsAll(selected)) throw invalid("采用的记忆已变化或不属于当前目标");
    return selected;
  }

  @Override
  @Transactional(readOnly=true)
  public List<String> recentQuestions(MemoryOwner owner,TopicKey topic,int limit) {
    return exposures.findByOwnerAndTopic(owner,topic,PageRequest.of(0,Math.min(50,Math.max(1,limit))))
        .stream().map(e -> e.toDomain().questionText()).toList();
  }

}
