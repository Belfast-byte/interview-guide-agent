package interview.guide.modules.llmprovider.dto;

public record CandidateProviderResponse(
    String id,
    String displayName,
    String baseUrl,
    String maskedApiKey,
    String model,
    String embeddingModel,
    Integer embeddingDimensions,
    boolean supportsEmbedding,
    Double temperature,
    boolean defaultChatProvider,
    boolean defaultEmbeddingProvider
) {}
