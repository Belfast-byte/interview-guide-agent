package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.observability.CodeAnalysisTelemetry;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CodeAnalysisResultAcceptanceServiceTest {

  @Mock
  private CodeAnalysisPersistenceService persistenceService;

  @Mock
  private CodeAnchorCatalog anchorCatalog;

  @Mock
  private CodeAnalysisTelemetry telemetry;

  private CodeAnalysisResultAcceptanceService service;

  @BeforeEach
  void setUp() {
    service = new CodeAnalysisResultAcceptanceService(
        persistenceService,
        anchorCatalog,
        new CodeAnalysisProperties(),
        telemetry
    );
  }

  @Test
  @DisplayName("分析 Agent 返回虚构锚点时不写入任何产物")
  void shouldRejectMissingAnchorBeforePersistence() {
    CodeAnchor anchor = new CodeAnchor("src/MissingService.java", 88);
    ProjectDigest digest = new ProjectDigest(
        "digest-1",
        "abc123",
        List.of("Java"),
        List.of(new ProjectDigest.ProjectModule("missing", "虚构模块", anchor)),
        List.of(),
        List.of()
    );
    CodeAnalysisResult result = new CodeAnalysisResult(
        digest,
        List.of(),
        List.of(),
        100,
        20
    );
    when(persistenceService.getRepositorySnapshot("job-1"))
        .thenReturn(new ProjectRepositorySnapshot("repos/one.zip", "abc123"));
    when(anchorCatalog.findMissing("repos/one.zip", Set.of(anchor)))
        .thenReturn(Set.of(anchor));

    assertThatThrownBy(() -> service.accept("job-1", result))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("src/MissingService.java:88");

    verify(persistenceService, never()).complete("job-1", result);
  }

  @Test
  @DisplayName("全部锚点真实存在时才进入短事务写入")
  void shouldPersistAfterAllAnchorsExist() {
    CodeAnchor anchor = new CodeAnchor("src/OrderService.java", 2);
    ProjectDigest digest = new ProjectDigest(
        "digest-1",
        "abc123",
        List.of("Java"),
        List.of(new ProjectDigest.ProjectModule("order", "订单模块", anchor)),
        List.of(),
        List.of()
    );
    CodeAnalysisResult result = new CodeAnalysisResult(
        digest,
        List.of(),
        List.of(),
        100,
        20
    );
    when(persistenceService.getRepositorySnapshot("job-1"))
        .thenReturn(new ProjectRepositorySnapshot("repos/one.zip", "abc123"));
    when(anchorCatalog.findMissing("repos/one.zip", Set.of(anchor)))
        .thenReturn(Set.of());

    service.accept("job-1", result);

    verify(persistenceService).complete("job-1", result);
  }

  @Test
  @DisplayName("分析成本超过单仓库预算时不读取快照也不写入产物")
  void shouldRejectTokenCostAboveBudget() {
    CodeAnalysisProperties properties = new CodeAnalysisProperties();
    properties.setMaxTokenCost(100);
    service = new CodeAnalysisResultAcceptanceService(
        persistenceService,
        anchorCatalog,
        properties,
        telemetry
    );
    ProjectDigest digest = new ProjectDigest(
        "digest-1",
        "abc123",
        List.of(),
        List.of(),
        List.of(),
        List.of()
    );
    CodeAnalysisResult result = new CodeAnalysisResult(
        digest,
        List.of(),
        List.of(),
        100,
        101
    );
    when(persistenceService.getRepositorySnapshot("job-1"))
        .thenReturn(new ProjectRepositorySnapshot("repos/one.zip", "abc123"));

    assertThatThrownBy(() -> service.accept("job-1", result))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("token");

    verifyNoInteractions(anchorCatalog);
    verify(persistenceService, never()).complete("job-1", result);
  }
}
