package interview.guide.modules.auth.api;

import interview.guide.modules.auth.persistence.UserEntity;

public record CurrentUserResponse(
    String candidateId,
    String email,
    String role
) {
  public static CurrentUserResponse from(UserEntity user) {
    return new CurrentUserResponse(
        user.id().toString(),
        user.email(),
        user.role().name()
    );
  }
}
