package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntent;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;

record ToolIntentExecution(
    ActionIntent intent,
    ReActRequest request
) {}
