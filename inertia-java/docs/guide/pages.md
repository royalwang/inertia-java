---
title: "Pages, controllers and components"
description: "Return typed InertiaResponse from @Controller, register allowed components, and keep REST separate."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/Application.java
  - inertia-java/examples/spring-react/frontend/src/pages.ts
  - inertia-java/docs/examples/first-application/HelloController.java
verification:
  - inertia-java/docs/scripts/verify-first-application.mjs
  - inertia-java/inertia-spring-webmvc/src/test/java/io/inertia/spring/InertiaHandlerValidatorTest.java
---

# Pages, controllers and components

A Page connects an application route to a registered frontend component. Java decides which data the current user may receive; React decides how to display it. Start from the [first application](../getting-started/first-application.md) if the asset/root/SSR configuration is not already in place.

## Add a page

1. Create the React component under your frontend's pages directory.
2. Add its name to the registry shared by the browser and Node entries. The example uses `frontend/src/pages.ts` and checks own keys with `Object.hasOwn`.
3. Add exactly the same name to `InertiaConfig.components`. Names are case-sensitive application identifiers, not Java class names or filesystem discovery rules.
4. Add an ordinary Spring `@Controller` GET method returning `InertiaResponse`, synchronously and without wrappers. Construct it with the component name and a `Props` value, or use `context.render(...)`.
5. Rebuild/restart Java and update the frontend using the selected development/production workflow.

The tutorial's canonical `HelloController.java` and `Hello.tsx` implement all these steps. Their creation script adds `Hello` to both registries. The checked-in example also maps `/users` to `Users/Index`, illustrating that URLs and component names need not match.

## Keep framework contracts separate

Inject `InertiaContext` or `InertiaRequest` as handler arguments when needed. They describe one request; never store them in singleton controller fields. Do authorization and input validation before defining data sources.

Typed Page handlers cannot use `@RestController`, `@ResponseBody`, `ResponseEntity<InertiaResponse>` or an asynchronous Page wrapper. Startup validation rejects those contracts instead of serializing a builder as ordinary JSON. Keep REST, upload/download and streaming methods on their normal framework path.

## Verify and recover

Load the route as a document with JavaScript disabled, then follow an official Link to it. Inspect the versioned Inertia response for the expected component and props. A Java allowlist mismatch fails rendering; a missing frontend registration prevents rendering/hydration. Fix the registries and rebuild the coherent frontend rather than accepting an empty page as success.

Use [response policy](responses.md) for status/headers and [shared data](shared-data.md) for common props. The Node renderer cannot supply a missing Java authorization decision.
