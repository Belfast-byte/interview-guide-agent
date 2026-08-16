package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 代码分析失败请求。
 */
public record CodeAnalysisFailureRequest(
    @NotBlank @Size(max = 200) String reason
) {}
