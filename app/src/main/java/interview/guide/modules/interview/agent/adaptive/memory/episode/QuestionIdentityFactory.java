package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerContext;
import org.springframework.stereotype.Component;

/** 从代码裁决的 Target 和模型问题文本生成 QuestionIdentity。 */
@Component
public class QuestionIdentityFactory {

  public QuestionIdentity create(CapabilityTarget target, RespondAction action) {
    return new QuestionIdentity(
        target.identity().topic(),
        target.identity().focus(),
        target.depth().expected(),
        target.depth().expected().name(),
        QuestionFingerprint.scenario(action.content()),
        QuestionFingerprint.wording(action.content())
    );
  }

  public QuestionIdentity create(InterviewerContext context, RespondAction action) {
    String objective = context.working().issue() == null
        ? context.working().attentionFocus()
        : context.working().issue().missingPoint();
    String difficulty = action.questionProvenance() == null
        ? context.working().expectedDepth().name()
        : action.questionProvenance().difficulty();
    return new QuestionIdentity(
        context.working().topic(),
        objective,
        context.working().expectedDepth(),
        difficulty,
        QuestionFingerprint.scenario(action.content()),
        QuestionFingerprint.wording(action.content())
    );
  }
}
