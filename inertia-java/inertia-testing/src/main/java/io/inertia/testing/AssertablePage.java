package io.inertia.testing;

import com.fasterxml.jackson.databind.JsonNode;
import io.inertia.core.PageCodec;
import java.util.regex.Pattern;

/** Small dependency-free Page assertions for server adapter tests. */
public final class AssertablePage {
  private final JsonNode page;
  private final PageCodec codec;

  private AssertablePage(JsonNode page, PageCodec codec) {
    this.page = page;
    this.codec = codec;
  }

  public static AssertablePage fromBody(String body) {
    var codec = new PageCodec();
    String json = body;
    if (body.stripLeading().startsWith("<")) {
      var match =
          Pattern.compile("<script[^>]*data-page=\"[^\"]+\"[^>]*>(.*?)</script>", Pattern.DOTALL)
              .matcher(body);
      if (!match.find()) throw new AssertionError("Missing Inertia page script");
      json = match.group(1);
    }
    return new AssertablePage(codec.read(json), codec);
  }

  public AssertablePage component(String expected) {
    return equals("/component", expected);
  }

  public AssertablePage equals(String pointer, Object expected) {
    if (!page.at(pointer).equals(codec.value(expected)))
      throw new AssertionError("Mismatch at " + pointer + ": " + page.at(pointer));
    return this;
  }

  public AssertablePage missing(String pointer) {
    if (!page.at(pointer).isMissingNode())
      throw new AssertionError("Unexpected Page value at " + pointer);
    return this;
  }

  public JsonNode data() {
    return page.deepCopy();
  }
}
