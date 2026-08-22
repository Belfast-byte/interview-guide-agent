package interview.guide.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.auth.jwt")
public record JwtProperties(
    @NotBlank String secret,
    @NotNull Duration ttl
) {}
