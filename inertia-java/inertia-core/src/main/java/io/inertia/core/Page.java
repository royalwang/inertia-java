package io.inertia.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

/** Serialized immutable-by-copy Page snapshot. */
public final class Page {
  private final ObjectNode data;

  /**
   * Validates required Page fields and snapshots all data, including optional metadata.
   *
   * @param data object containing textual component/url/version and object-valued props
   * @throws NullPointerException if data is null
   * @throws IllegalArgumentException if required fields are absent or have incorrect JSON types
   */
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

  /**
   * Returns an independent copy of the complete Page tree.
   *
   * @return mutable copy; changes cannot alter this Page
   */
  public ObjectNode data() {
    return data.deepCopy();
  }

  /**
   * Returns the validated component field.
   *
   * @return component name; registry membership is checked by the renderer
   */
  public String component() {
    return data.get("component").asText();
  }
}
