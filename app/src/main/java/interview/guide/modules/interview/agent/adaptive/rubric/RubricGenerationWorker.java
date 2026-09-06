package interview.guide.modules.interview.agent.adaptive.rubric;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@ConditionalOnProperty(prefix="app.interview.adaptive-agent", name="enabled", havingValue="true")
public class RubricGenerationWorker {
  private final RubricGenerationStore store;
  private final RubricGenerationModels models;
  public RubricGenerationWorker(RubricGenerationStore store, RubricGenerationModels models) {
    this.store=store; this.models=models;
  }
  @Scheduled(scheduler="rubricGenerationScheduler", fixedDelayString="${app.interview.adaptive-agent.rubric-generation-delay:30000}", initialDelayString="${app.interview.adaptive-agent.rubric-generation-delay:30000}")
  public void recover() {
    for(String id:store.pending()) process(id);
  }
  public void process(String id) {
    var claim=store.claim(id); if(claim==null) return;
    try {
      var draft=claim.draft() != null ? claim.draft() : models.generate(claim.dimension(),claim.focus(),claim.question());
      if(draft==null || !draft.valid()) throw new IllegalArgumentException("invalid draft");
      if (claim.draft() == null) store.saveDraft(claim,draft,models.generatorProvider());
      var review=models.judge(draft);
      store.complete(claim,draft,review,models.generatorProvider(),models.judgeProvider());
    } catch(RuntimeException e) {
      store.failed(claim,e.getClass().getSimpleName());
      log.warn("Rubric generation failed: job={}, type={}",id,e.getClass().getSimpleName());
    }
  }
}
