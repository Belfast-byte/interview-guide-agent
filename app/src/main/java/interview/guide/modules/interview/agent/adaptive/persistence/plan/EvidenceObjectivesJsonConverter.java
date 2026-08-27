package interview.guide.modules.interview.agent.adaptive.persistence.plan;

import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 能力目标证据要求的 JSON 映射。 */
@Converter
public class EvidenceObjectivesJsonConverter implements
    AttributeConverter<List<CapabilityTarget.EvidenceObjective>, String> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<List<CapabilityTarget.EvidenceObjective>> TYPE =
      new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(
      List<CapabilityTarget.EvidenceObjective> objectives
  ) {
    try {
      return OBJECT_MAPPER.writeValueAsString(objectives);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("证据目标序列化失败", e);
    }
  }

  @Override
  public List<CapabilityTarget.EvidenceObjective> convertToEntityAttribute(String json) {
    try {
      return List.copyOf(OBJECT_MAPPER.readValue(json, TYPE));
    } catch (JacksonException e) {
      throw new IllegalArgumentException("证据目标反序列化失败", e);
    }
  }
}
