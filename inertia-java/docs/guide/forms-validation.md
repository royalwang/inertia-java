---
title: "Forms and validation"
description: "Use Jakarta/BindingResult, default/named bags, redirect errors and explicit resubmission."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/Application.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/AdvancedDemo.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/ValidationBridge.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/JakartaValidationBridge.java
verification:
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
  - inertia-java/examples/spring-react/frontend/e2e/advanced.spec.ts
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/ValidationBridgeTest.java
---

# Forms and validation

Use an Inertia form to submit to a Java route, validate server-side, then redirect to a Page carrying errors or flash. This keeps navigation, form state and validation within the official client's lifecycle.

## Implement the round trip

1. Create a React `useForm` with initial field values and submit it to the controller route.
2. In Java, bind input and validate it before performing business work. The sample `/users` handler uses a Jakarta-validated record and `BindingResult`.
3. On failure, copy safe errors with `ValidationBridge.errors(errors)` and return `context.backWithErrors(...)`, or queue errors and choose a fixed redirect destination.
4. On success, complete your authorized business operation, queue a safe flash message and return a redirect.
5. Render errors from the form and flash from the redirected Page. The included examples validate input but do not persist a user.

The application must separately add a Jakarta validation provider. `JakartaValidationBridge.errors(violations)` is another bridge for explicit validator results. Bridges copy paths/messages without rejected values. Ordinary REST validation keeps Spring's native semantics; it does not automatically become an Inertia redirect.

## Isolate multiple forms

Default errors are delivered under the requested `X-Inertia-Error-Bag` when appropriate. Explicit named bags use `context.withErrors(bag, errors)`. The Advanced example submits `profile` and `team` forms with the same field name and distinct bags; each form receives its own feedback.

`ValidationErrors` retains ordered messages. Default presentation uses the first message per field; config `withAllErrors(true)` or the Boot `inertia.all-errors` override preserves all messages. The frontend must handle arrays in that mode.

## Preserve security and failure feedback

Use the application's [CSRF integration](csrf.md). A rejected write must not be replayed automatically. Validation is not authorization; check the current user's permission before looking up or mutating protected records. Avoid echoing credentials or rejected sensitive values through errors, flash or logs.

Run the example and submit blank, invalid and valid names. Check redirect status, returned errors, processing state and one-shot flash. At `/advanced`, alternate invalid/successful submissions between forms and confirm their local errors stay independent. Browser tests assert those rendered states, while bridge tests cover message/path copying. Read [flash delivery](flash-session.md) for failure and concurrency boundaries.
