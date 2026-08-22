package interview.guide.common.security;

import interview.guide.common.aspect.RateLimitAspect;
import interview.guide.modules.auth.domain.UserRole;
import interview.guide.modules.auth.persistence.UserEntity;
import interview.guide.modules.interview.listener.EvaluateStreamConsumer;
import interview.guide.modules.knowledgebase.listener.QuestionGenStreamConsumer;
import interview.guide.modules.knowledgebase.listener.VectorizeStreamConsumer;
import interview.guide.modules.resume.listener.AnalyzeStreamConsumer;
import interview.guide.modules.voiceinterview.handler.VoiceInterviewWebSocketHandler;
import interview.guide.modules.voiceinterview.listener.VoiceEvaluateStreamConsumer;
import jakarta.servlet.Filter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.redisson.api.RedissonClient;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.interview.adaptive-agent.enabled=false"
)
@ActiveProfiles("test")
class SecurityBoundaryIntegrationTest {

  @Autowired
  private WebApplicationContext context;
  @Autowired
  private JwtTokenService tokenService;
  @Autowired
  private Filter springSecurityFilterChain;
  @MockitoBean
  private RedissonClient redissonClient;
  @MockitoBean
  private RateLimitAspect rateLimitAspect;
  @MockitoBean
  private AnalyzeStreamConsumer analyzeStreamConsumer;
  @MockitoBean
  private EvaluateStreamConsumer evaluateStreamConsumer;
  @MockitoBean
  private QuestionGenStreamConsumer questionGenStreamConsumer;
  @MockitoBean
  private VectorizeStreamConsumer vectorizeStreamConsumer;
  @MockitoBean
  private VoiceEvaluateStreamConsumer voiceEvaluateStreamConsumer;
  @MockitoBean
  private VoiceInterviewWebSocketHandler voiceInterviewWebSocketHandler;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() throws Throwable {
    when(rateLimitAspect.around(any())).thenAnswer(invocation ->
        ((ProceedingJoinPoint) invocation.getArgument(0)).proceed());
    mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .addFilters(springSecurityFilterChain)
        .build();
  }

  @Test
  @DisplayName("匿名访问候选人接口返回 401")
  void shouldRejectAnonymousCandidateRequest() throws Exception {
    mockMvc.perform(get("/api/resumes/health"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(401));
  }

  @Test
  @DisplayName("候选人可以访问候选人接口")
  void shouldAllowCandidateRequest() throws Exception {
    mockMvc.perform(get("/api/resumes/health")
            .header("Authorization", bearer(UserRole.CANDIDATE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200));
  }

  @Test
  @DisplayName("管理员不能读取候选人业务接口")
  void shouldRejectAdminFromCandidateData() throws Exception {
    mockMvc.perform(get("/api/resumes/health")
            .header("Authorization", bearer(UserRole.ADMIN)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(403));
  }

  @Test
  @DisplayName("候选人不能访问管理员接口")
  void shouldRejectCandidateFromAdminApi() throws Exception {
    mockMvc.perform(get("/api/llm-provider/providers")
            .header("Authorization", bearer(UserRole.CANDIDATE)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(403));
  }

  @Test
  @DisplayName("候选人可以访问自己的 Provider 接口")
  void shouldAllowCandidateProviderRequest() throws Exception {
    mockMvc.perform(get("/api/me/llm-providers")
            .header("Authorization", bearer(UserRole.CANDIDATE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200));
  }

  @Test
  @DisplayName("管理员不能访问候选人 Provider 接口")
  void shouldRejectAdminFromCandidateProviderRequest() throws Exception {
    mockMvc.perform(get("/api/me/llm-providers")
            .header("Authorization", bearer(UserRole.ADMIN)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(403));
  }

  @Test
  @DisplayName("候选人可以通过 HTTP 注册并登录")
  void shouldRegisterAndLoginThroughHttp() throws Exception {
    String credentials = "{\"email\":\"http-user@example.com\",\"password\":\"password123\"}";

    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(credentials))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.role").value("CANDIDATE"));

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(credentials))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.data.expiresInSeconds").value(604800))
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
  }

  private String bearer(UserRole role) {
    UserEntity user = new UserEntity(role.name().toLowerCase() + "@example.com", "hash", role);
    return "Bearer " + tokenService.issue(user);
  }
}
