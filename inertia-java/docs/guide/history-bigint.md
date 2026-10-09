---
title: "History and exact integers"
description: "Use history controls and bigint across props/flash, and explain browser key clearing."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PageCodec.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/InertiaResponse.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/DemoHistory.java
  - inertia-java/examples/spring-react/frontend/src/app.tsx
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/HistoryOverridesTest.java
  - inertia-java/examples/spring-react/frontend/e2e/history.spec.ts
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
---

# History and exact integers

History presentation and integer serialization affect browser state. Configure them with the matching official client and SSR entries, and verify browser behavior rather than only checking JSON flags.

## History policy

`InertiaConfig.encryptHistory` sets the application default. A context override changes that request; a response override wins over both. A context encryption override does not persist through a redirect. `clearHistory` can be queued for delivery, while a response can set the current Page instruction directly.

The opt-in `--inertia.demo-history-enabled=true` example provides encrypted, plain, clear and CSR Pages under `/demo-history/{mode}`. It demonstrates presentation behavior; it is separate from authentication. The identity example combines fresh-session login, encrypted account history and logout cleanup.

Encrypted history and clear-history instructions do not authorize future requests or guarantee another tab has removed its DOM. Continue enforcing server permissions and private/no-store caching. Cross-tab notification is a convenience with its own storage availability limits.

## Preserve exact identifiers

JavaScript Number cannot exactly represent integers beyond ±9007199254740991. Enable `preserveBigIntegers` in configuration or on the response when sending larger IDs. The codec emits a `$bigint` marker with the decimal string and the locked client/server entries perform the matching revival.

Do not convert the value through Number before revival. Display bigint values with `String(value)` and avoid arithmetic that mixes Number and bigint. The example's `9007199254740993L` is intentionally outside the safe range and must remain exact through SSR, Page transport and browser display.

The setting also affects nested values and flash. Response settings override config. Applications choosing string IDs instead should define that DTO contract consistently rather than partially enabling revival on only one entry.

## Verify both representations

Inspect exact Page JSON markers, JavaScript-disabled server HTML, hydrated text and browser back/forward state. Run the history and CSP/browser scenarios when modifying entries, root IDs or serialization. A JSON flag alone cannot prove encryption or hydration worked.

Use the [rendering model](../concepts/rendering.md) for SSR/CSR distinctions and [build versioning](../concepts/versioning.md) before changing the locked client or bundle format.

## Upstream references

- [official history encryption documentation](https://inertiajs.com/docs/v3/security/history-encryption)
