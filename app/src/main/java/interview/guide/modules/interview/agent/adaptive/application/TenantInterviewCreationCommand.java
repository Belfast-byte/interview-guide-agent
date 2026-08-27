package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.session.InterviewSessionSettings;

/** 租户调用创建面试所需参数。 */
public record TenantInterviewCreationCommand(
    String tenantId,
    String candidateId,
    String jd,
    String resume,
    String llmProvider,
    InterviewSessionSettings settings
) {}
