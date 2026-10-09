---
title: "Root templates and CSP"
description: "Insert trusted SSR body once, escape view data, pass a server nonce and customize root IDs."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/RootView.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PageCodec.java
  - inertia-java/inertia-spring-webmvc/src/main/java/io/inertia/spring/InertiaMvcConfigurer.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/CspFilter.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/CspNonceTest.java
  - inertia-java/examples/spring-react/frontend/scripts/verify-csp.mjs
  - inertia-java/examples/spring-react/frontend/scripts/verify-custom-root.mjs
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
---

# Root templates and CSP

The application root view joins asset tags, trusted rendered head/body and Page data into a document. Treat that join as a serialization/security boundary rather than concatenating arbitrary user strings into HTML.

## Root-view contract

`RootView.View` exposes the Page, SSR head/body, whether SSR was used, template-only data and an optional validated nonce. The supplied body already contains the expected root/Page script in the locked rendering contract. Insert it once; adding another root or another Page payload can break hydration and leak inconsistent state.

`response.withViewData(...)` supplies template data, not Page props. Escape ordinary template values in their HTML context. Use `PageCodec.htmlJson` when implementing a Page script boundary; it escapes dangerous HTML/script delimiters and line separators. Raw object JSON is not automatically safe inside a script element.

Align `InertiaConfig.rootId`, the gateway's expected root ID, Node's `SSR_ROOT_ID` and the browser entry's root lookup. The example's root meta tag tells the browser entry which ID to use. Root IDs have a restricted safe syntax.

## Supply a CSP nonce

In Spring MVC, a trusted filter can set `InertiaMvcConfigurer.CSP_NONCE_ATTRIBUTE` before adapter snapshotting. The same request nonce reaches the root view, Page script and Vite tags. The browser entry passes it to the official Inertia app setup for client-created elements.

The sample `CspFilter` generates a fresh random nonce and sets its opt-in policy. Its development allowances are specific local origins; they are not a general production policy. Decide trusted sources, style policy, HTTPS and proxy behavior in the host application. Never accept a nonce from arbitrary incoming headers.

## Verify the boundary

Test hostile strings through SSR and CSR, then verify the same nonce appears on the intended scripts and untrusted inline scripts are blocked. A header's presence alone does not prove the app still hydrates. Run the example's CSP/custom-root contracts when changing root layout, entries or gateway checks.

The minimal `RootView` is useful for API examples but adds no client asset tags. Use [SSR setup](setup.md) for a complete browser configuration and [exact integers](../guide/history-bigint.md) when changing Page serialization.

## Upstream references

- [W3C CSP3 working draft](https://www.w3.org/TR/CSP3/#strict-dynamic-usage)
