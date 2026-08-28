package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;

/** 通过重复检查后，可与 turn 一起原子发布的问题。 */
public record QuestionPublication(
    RespondAction action,
    QuestionIdentity identity,
    Long sourceExposureId,
    Long sourceEpisodeId
) {}
