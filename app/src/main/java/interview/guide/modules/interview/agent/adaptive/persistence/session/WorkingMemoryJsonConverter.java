package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 将 Turn 边界的 WorkingMemory Snapshot 映射为 JSON 文本。 */
@Converter
public class WorkingMemoryJsonConverter
    implements AttributeConverter<WorkingMemory, String> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(WorkingMemory memory) {
    if (memory == null) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(memory);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("WorkingMemory 序列化失败", e);
    }
  }

  @Override
  public WorkingMemory convertToEntityAttribute(String json) {
    if (json == null) {
      return null;
    }
    try {
      return OBJECT_MAPPER.readValue(json, WorkingMemory.class);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("WorkingMemory 反序列化失败", e);
    }
  }
}
