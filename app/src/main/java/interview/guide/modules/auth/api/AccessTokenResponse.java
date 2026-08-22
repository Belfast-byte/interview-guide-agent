package interview.guide.modules.auth.api;

public record AccessTokenResponse(
    String accessToken,
    String tokenType,
    long expiresInSeconds
) {}
