package interview.guide.modules.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.security.JwtTokenService;
import interview.guide.modules.auth.domain.UserRole;
import interview.guide.modules.auth.persistence.UserEntity;
import interview.guide.modules.auth.persistence.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private JwtTokenService tokenService;

  private AuthService service;

  @BeforeEach
  void setUp() {
    service = new AuthService(userRepository, passwordEncoder, tokenService);
  }

  @Test
  @DisplayName("注册时规范化邮箱并只保存 BCrypt 结果")
  void shouldNormalizeEmailAndPersistPasswordHash() {
    when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
    when(userRepository.saveAndFlush(any(UserEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    UserEntity registered = service.register(" Candidate@Example.COM ", "password123");

    ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).saveAndFlush(captor.capture());
    assertThat(registered.email()).isEqualTo("candidate@example.com");
    assertThat(captor.getValue().passwordHash()).isEqualTo("bcrypt-hash");
    assertThat(registered.role()).isEqualTo(UserRole.CANDIDATE);
  }

  @Test
  @DisplayName("登录失败不区分邮箱不存在与密码错误")
  void shouldExposeSameFailureForUnknownEmail() {
    when(userRepository.findByEmail("missing@example.com"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.login("missing@example.com", "password123"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("邮箱或密码错误");
  }

  @Test
  @DisplayName("登录成功签发 Bearer Token")
  void shouldIssueAccessToken() {
    UserEntity user = new UserEntity("candidate@example.com", "hash", UserRole.CANDIDATE);
    when(userRepository.findByEmail("candidate@example.com"))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches("password123", "hash")).thenReturn(true);
    when(tokenService.issue(user)).thenReturn("signed-token");
    when(tokenService.expiresInSeconds()).thenReturn(604800L);

    var token = service.login("candidate@example.com", "password123");

    assertThat(token.accessToken()).isEqualTo("signed-token");
    assertThat(token.tokenType()).isEqualTo("Bearer");
    assertThat(token.expiresInSeconds()).isEqualTo(604800L);
  }
}
