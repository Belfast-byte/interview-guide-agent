package interview.guide.modules.interview.agent.adaptive.tool;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 工具配置属性。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app.interview.adaptive-agent.tools")
public class ToolProperties {

  @Min(1)
  private int maxResultChars = 8_000;

  @Min(1)
  @Max(20)
  private int questionBankLimit = 5;

  @Min(1)
  @Max(20)
  private int questionIndexBatchSize = 10;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  private double questionBankMinScore = 0.3;

  @Min(1)
  @Max(20)
  private int rubricSearchLimit = 5;

  @Min(1)
  @Max(100)
  private int rubricIndexBatchSize = 10;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  private double rubricMinScore = 0.3;
}
