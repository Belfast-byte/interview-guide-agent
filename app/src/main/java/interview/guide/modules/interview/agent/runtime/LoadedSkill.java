package interview.guide.modules.interview.agent.runtime;

public record LoadedSkill(
    String id,
    String name,
    String description,
    String body,
    String hash
) {}
