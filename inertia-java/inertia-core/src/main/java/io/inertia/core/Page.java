package io.inertia.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

/** Serialized immutable-by-copy Page snapshot. */
public final class Page {
  private final ObjectNode data;

  public Page(ObjectNode data) {
    Objects.requireNonNull(data);
    for (String key : new String[] {"component", "props", "url", "version"})
      if (!data.has(key)) throw new IllegalArgumentException("Missing Page field: " + key);
    if (!data.get("component").isTextual()
        || !data.get("props").isObject()
        || !data.get("url").isTextual()
        || !data.get("version").isTextual())
      throw new IllegalArgumentException("Invalid Page fields");
    this.data = data.deepCopy();
  }

  public ObjectNode data() {
    return data.deepCopy();
  }

  public String component() {
    return data.get("component").asText();
  }
}
