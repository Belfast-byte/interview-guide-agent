package interview.guide.modules.auth.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.security.JwtTokenService;
import interview.guide.modules.auth.api.AccessTokenResponse;
import interview.guide.modules.auth.domain.UserRole;
import interview.guide.modules.auth.persistence.UserEntity;
import interview.guide.modules.auth.persistence.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private static final String TOKEN_TYPE = "Bearer";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenService tokenService;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenService tokenService
  ) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.tokenService = tokenService;
  }

  @Transactional
  public UserEntity register(String rawEmail, String password) {
    String email = normalizeEmail(rawEmail);
    if (userRepository.existsByEmail(email)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "邮箱已注册");
    }
    try {
      return userRepository.saveAndFlush(new UserEntity(
          email,
          passwordEncoder.encode(password),
          UserRole.CANDIDATE
      ));
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "邮箱已注册", exception);
    }
  }

  @Transactional(readOnly = true)
  public AccessTokenResponse login(String rawEmail, String password) {
    String email = normalizeEmail(rawEmail);
    UserEntity user = userRepository.findByEmail(email).orElse(null);
    if (user == null || !passwordEncoder.matches(password, user.passwordHash())) {
      log.warn("登录失败: email={}", email);
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "邮箱或密码错误");
    }
    return new AccessTokenResponse(
        tokenService.issue(user),
        TOKEN_TYPE,
        tokenService.expiresInSeconds()
    );
  }

  @Transactional(readOnly = true)
  public UserEntity currentUser(UUID userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
