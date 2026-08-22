package interview.guide.common.security;

import interview.guide.modules.auth.domain.UserRole;
import interview.guide.modules.auth.persistence.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

  private static final String ROLE_CLAIM = "role";

  private final JwtProperties properties;
  private final SecretKey signingKey;
  private final Clock clock;

  @Autowired
  public JwtTokenService(JwtProperties properties) {
    this(properties, Clock.systemUTC());
  }

  JwtTokenService(JwtProperties properties, Clock clock) {
    this.properties = properties;
    this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
    this.clock = clock;
  }

  public String issue(UserEntity user) {
    Instant issuedAt = clock.instant();
    return Jwts.builder()
        .subject(user.id().toString())
        .claim(ROLE_CLAIM, user.role().name())
        .issuedAt(Date.from(issuedAt))
        .expiration(Date.from(issuedAt.plus(properties.ttl())))
        .signWith(signingKey)
        .compact();
  }

  public AuthenticatedUser parse(String token) {
    Claims claims = Jwts.parser()
        .verifyWith(signingKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
    return new AuthenticatedUser(
        UUID.fromString(claims.getSubject()),
        UserRole.valueOf(claims.get(ROLE_CLAIM, String.class))
    );
  }

  public long expiresInSeconds() {
    return properties.ttl().toSeconds();
  }
}
