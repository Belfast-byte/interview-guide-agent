以下是本次模型调用的完整上下文：

<data-boundary>
{contextJson}
</data-boundary>

<episode-memory-contract>
interviewContext.episodeHistory 是唯一允许使用的跨场历史记忆，只包含 skillId、focusId、depthLevel、errorTags、answerHabitTags、createdAt。
历史原题、回答、摘要、评级理由、证据原文和维度简报均未提供；不得补写、猜测或索取这些内容。
episodeHistory 只帮助选择本轮提问方向，不得用于评价当前回答。
</episode-memory-contract>

请调用一个必要工具，或返回下一步 ASK 决策。
