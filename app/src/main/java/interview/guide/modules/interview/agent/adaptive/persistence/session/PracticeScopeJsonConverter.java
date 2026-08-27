package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 将练习 TopicKey 范围保存为 JSON 文本。 */
@Converter
public class PracticeScopeJsonConverter
    implements AttributeConverter<PracticeScope, String> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<List<TopicKey>> TOPICS_TYPE = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(PracticeScope scope) {
    try {
      return OBJECT_MAPPER.writeValueAsString(scope.topics());
    } catch (JacksonException e) {
      throw new IllegalArgumentException("练习范围序列化失败", e);
    }
  }

  @Override
  public PracticeScope convertToEntityAttribute(String json) {
    try {
      return new PracticeScope(OBJECT_MAPPER.readValue(json, TOPICS_TYPE));
    } catch (JacksonException e) {
      throw new IllegalArgumentException("练习范围反序列化失败", e);
    }
  }
}
