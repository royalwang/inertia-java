package io.inertia.spring;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

class ValidationBridgeTest {
  @Test
  void copiesMessagesWithoutRejectedValuesAndPreservesAllErrors() {
    var result = new BeanPropertyBindingResult(new Object(), "form");
    result.addError(
        new FieldError(
            "form", "password", "secret-rejected-value", false, null, null, "Too short"));
    result.addError(new FieldError("form", "password", "Use a symbol"));
    result.addError(new FieldError("form", "items[1].name", "Required nested name"));
    result.addError(new ObjectError("form", "Invalid form"));
    var first = ValidationBridge.firstErrors(result);
    assertEquals("Too short", first.get("password"));
    assertEquals("Required nested name", first.get("items.1.name"));
    assertEquals(
        List.of("Too short", "Use a symbol"),
        ValidationBridge.errors(result).messages().get("password"));
    assertEquals("Invalid form", first.get("_form"));
    assertFalse(first.toString().contains("secret-rejected-value"));
    var all = ValidationBridge.allErrors(result);
    assertEquals(List.of("Too short", "Use a symbol"), all.get("password"));
    assertThrows(UnsupportedOperationException.class, () -> all.get("password").add("change"));
    assertThrows(UnsupportedOperationException.class, () -> first.put("other", "change"));
  }
}
