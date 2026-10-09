package io.inertia.testing;

import com.fasterxml.jackson.databind.JsonNode;
import io.inertia.core.PageCodec;
import java.util.regex.Pattern;

/**
 * Fluent Page assertions without a dependency on a particular test framework.
 *
 * <p>Supports Page JSON and the standard root-view JSON script. These assertions inspect the Page
 * payload only; test HTTP status, headers, CSRF, and browser hydration separately.
 */
public final class AssertablePage {
  private final JsonNode page;
  private final PageCodec codec;

  private AssertablePage(JsonNode page, PageCodec codec) {
    this.page = page;
    this.codec = codec;
  }

  /**
   * Parses Page JSON or extracts JSON from the standard HTML {@code data-page} script.
   *
   * <p>HTML detection ignores leading whitespace. The script matcher expects a double-quoted {@code
   * data-page} attribute; it is not a general-purpose HTML parser.
   *
   * @param body response body containing JSON or the standard root-view HTML
   * @return assertions over the parsed JSON
   * @throws AssertionError if HTML contains no matching Page script
   * @throws IllegalArgumentException if the extracted JSON cannot be decoded
   */
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

  /**
   * Asserts the Page component using JSON value equality.
   *
   * @param expected expected component name
   * @return this assertion object for chaining
   * @throws AssertionError if the component differs
   */
  public AssertablePage component(String expected) {
    return equals("/component", expected);
  }

  /**
   * Asserts equality after converting the expected value with the Page codec.
   *
   * @param pointer JSON Pointer such as {@code /props/user/name}, not a dot-separated property path
   * @param expected value to convert to JSON and compare
   * @return this assertion object for chaining
   * @throws AssertionError if the selected value differs
   * @throws IllegalArgumentException if the pointer or expected value cannot be interpreted
   */
  public AssertablePage equals(String pointer, Object expected) {
    if (!page.at(pointer).equals(codec.value(expected)))
      throw new AssertionError("Mismatch at " + pointer + ": " + page.at(pointer));
    return this;
  }

  /**
   * Asserts that a JSON Pointer has no value; an explicit JSON null is still present.
   *
   * @param pointer JSON Pointer to inspect
   * @return this assertion object for chaining
   * @throws AssertionError if the selected value exists, including null
   * @throws IllegalArgumentException if the pointer is invalid
   */
  public AssertablePage missing(String pointer) {
    if (!page.at(pointer).isMissingNode())
      throw new AssertionError("Unexpected Page value at " + pointer);
    return this;
  }

  /**
   * Returns a defensive copy of the parsed Page data.
   *
   * @return mutable copy whose changes do not affect subsequent assertions
   */
  public JsonNode data() {
    return page.deepCopy();
  }
}
