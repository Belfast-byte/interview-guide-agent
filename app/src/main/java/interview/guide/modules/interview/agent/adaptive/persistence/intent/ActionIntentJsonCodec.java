package interview.guide.modules.interview.agent.adaptive.persistence.intent;

import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentPayload;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentType;
import interview.guide.modules.interview.agent.adaptive.core.intent.AskActionPayload;
import interview.guide.modules.interview.agent.adaptive.core.intent.ToolActionPayload;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ActionIntentJsonCodec {

  private final ObjectMapper objectMapper;

  public ActionIntentJsonCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String encode(ActionIntentPayload payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("ActionIntent payload 序列化失败", e);
    }
  }

  public ActionIntentPayload decode(ActionIntentType type, String json) {
    try {
      return switch (type) {
        case ASK -> objectMapper.readValue(json, AskActionPayload.class);
        case CALL_TOOL -> objectMapper.readValue(json, ToolActionPayload.class);
      };
    } catch (JacksonException e) {
      throw new IllegalArgumentException("ActionIntent payload 反序列化失败", e);
    }
  }
}
