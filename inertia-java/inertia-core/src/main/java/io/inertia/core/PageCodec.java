package io.inertia.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigInteger;

public final class PageCodec {
  private final ObjectMapper mapper;
  private static final BigInteger SAFE = BigInteger.valueOf(9007199254740991L);

  public PageCodec() {
    this(new ObjectMapper());
  }

  public PageCodec(ObjectMapper mapper) {
    this.mapper = mapper.copy();
  }

  public ObjectNode object() {
    return mapper.createObjectNode();
  }

  public JsonNode value(Object value) {
    return mapper.valueToTree(value);
  }

  public JsonNode read(String json) {
    try {
      return mapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid JSON", e);
    }
  }

  public String json(Page page) {
    return json(page.data());
  }

  public String json(JsonNode value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Cannot serialize Page", e);
    }
  }

  public String htmlJson(Page page) {
    return json(page)
        .replace("/", "\\/")
        .replace("<", "\\u003c")
        .replace(">", "\\u003e")
        .replace("&", "\\u0026")
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029");
  }

  public JsonNode bigIntegers(JsonNode input) {
    if (input.isIntegralNumber() && input.bigIntegerValue().abs().compareTo(SAFE) > 0)
      return object().put("$bigint", input.bigIntegerValue().toString());
    if (input.isObject()) {
      var out = object();
      input.fields().forEachRemaining(e -> out.set(e.getKey(), bigIntegers(e.getValue())));
      return out;
    }
    if (input.isArray()) {
      var out = mapper.createArrayNode();
      input.forEach(n -> out.add(bigIntegers(n)));
      return out;
    }
    return input.deepCopy();
  }
}
