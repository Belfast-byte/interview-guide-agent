package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.StablePattern;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

@Converter
public class StablePatternsJsonConverter
    implements AttributeConverter<List<StablePattern>, String> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<List<StablePattern>> TYPE = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(List<StablePattern> patterns) {
    try {
      return OBJECT_MAPPER.writeValueAsString(patterns);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Semantic stable patterns 序列化失败", e);
    }
  }

  @Override
  public List<StablePattern> convertToEntityAttribute(String json) {
    try {
      return OBJECT_MAPPER.readValue(json, TYPE);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Semantic stable patterns 反序列化失败", e);
    }
  }
}
