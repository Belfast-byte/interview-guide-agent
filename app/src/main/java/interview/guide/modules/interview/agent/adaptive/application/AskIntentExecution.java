package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntent;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import java.util.function.Consumer;

record AskIntentExecution(
    ActionIntent intent,
    ReActRequest request,
    Consumer<String> deltaSink
) {}
