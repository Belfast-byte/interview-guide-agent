package interview.guide.modules.llmprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCandidateProviderRequest(
    @NotBlank(message = "Provider 名称不能为空")
    @Size(max = 128, message = "Provider 名称不能超过 128 个字符")
    String displayName,
    @NotBlank(message = "Base URL 不能为空")
    @Size(max = 512, message = "Base URL 不能超过 512 个字符")
    String baseUrl,
    @NotBlank(message = "API Key 不能为空")
    @Size(max = 4096, message = "API Key 不能超过 4096 个字符")
    String apiKey,
    @NotBlank(message = "文本模型不能为空")
    @Size(max = 128, message = "文本模型不能超过 128 个字符")
    String model,
    @Size(max = 128, message = "嵌入模型不能超过 128 个字符")
    String embeddingModel,
    @Positive(message = "嵌入维度必须为正整数")
    Integer embeddingDimensions,
    Double temperature,
    boolean thinkingDisabled
) {}
