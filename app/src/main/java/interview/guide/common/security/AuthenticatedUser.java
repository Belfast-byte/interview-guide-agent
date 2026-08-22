package interview.guide.common.security;

import interview.guide.modules.auth.domain.UserRole;
import java.util.UUID;

public record AuthenticatedUser(UUID candidateId, UserRole role) {}
