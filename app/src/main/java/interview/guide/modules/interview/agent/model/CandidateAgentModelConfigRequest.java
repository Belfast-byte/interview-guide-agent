package interview.guide.modules.interview.agent.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CandidateAgentModelConfigRequest(
    @NotBlank(message = "模型服务地址不能为空")
    @Size(max = 512, message = "模型服务地址不能超过 512 个字符")
    String baseUrl,
    @Size(max = 4096, message = "API Key 不能超过 4096 个字符")
    String apiKey,
    @NotBlank(message = "模型名称不能为空")
    @Size(max = 128, message = "模型名称不能超过 128 个字符")
    String model,
    @DecimalMin(value = "0.0", message = "temperature 不能小于 0")
    @DecimalMax(value = "2.0", message = "temperature 不能大于 2")
    Double temperature
) {}
