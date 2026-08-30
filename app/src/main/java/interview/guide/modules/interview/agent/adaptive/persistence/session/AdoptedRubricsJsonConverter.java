package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.session.AdoptedRubricSource;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 将 Turn 采用的 rubric provenance 映射为 JSON。 */
@Converter
public class AdoptedRubricsJsonConverter
    implements AttributeConverter<List<AdoptedRubricSource>, String> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<List<AdoptedRubricSource>> TYPE = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(List<AdoptedRubricSource> sources) {
    try {
      return OBJECT_MAPPER.writeValueAsString(sources);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("rubric provenance 序列化失败", e);
    }
  }

  @Override
  public List<AdoptedRubricSource> convertToEntityAttribute(String json) {
    try {
      return List.copyOf(OBJECT_MAPPER.readValue(json, TYPE));
    } catch (JacksonException e) {
      throw new IllegalArgumentException("rubric provenance 反序列化失败", e);
    }
  }
}
