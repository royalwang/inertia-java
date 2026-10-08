package io.inertia.spring;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.*;
import jakarta.validation.constraints.*;
import java.util.*;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

class JakartaValidationBridgeTest {
  record Item(@NotBlank(message = "Required") @Size(min = 3, message = "Too short") String name) {}

  record Form(
      @Valid List<Item> items,
      List<@NotBlank(message = "Tag required") String> tags,
      Map<String, @Valid Item> byKey,
      @Size(min = 30, message = "Too short password") String password) {}

  @Test
  void realValidatorPreservesIndexedAndMapPathsAndNeverIncludesInvalidValues() {
    try (var factory =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()) {
      var form =
          new Form(
              List.of(new Item("")),
              List.of(""),
              Map.of("primary", new Item("")),
              "secret-password");
      var violations = factory.getValidator().validate(form);
      var errors = JakartaValidationBridge.errors(violations);
      assertEquals(List.of("Required", "Too short"), errors.messages().get("items.0.name"));
      assertEquals(List.of("Tag required"), errors.messages().get("tags.0"));
      assertEquals(List.of("Required", "Too short"), errors.messages().get("byKey.primary.name"));
      assertEquals(List.of("Too short password"), errors.messages().get("password"));
      assertFalse(errors.messages().toString().contains("secret-password"));
      assertEquals(
          errors.messages(), JakartaValidationBridge.errors(new HashSet<>(violations)).messages());
    }
  }
}
