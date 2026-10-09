---
title: "Infinite scroll and paginator metadata"
description: "Adapt a custom ScrollPage and match pageName/direction/reset to client collections."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/ScrollPage.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Prop.java
  - inertia-java/examples/spring-react/frontend/src/pages/Feed.tsx
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/RustParityTest.java
  - inertia-java/inertia-core/src/test/java/io/inertia/core/AdvancedPropsTest.java
  - inertia-java/examples/spring-react/frontend/e2e/flows.spec.ts
---

# Infinite scroll and paginator metadata

Infinite scroll combines application pagination with client merge instructions. The library describes a page of data; it does not query a database or determine which rows a user may access.

## Build a page

`ScrollPage` carries data, query page-name, previous/next/current identifiers and a wrapper key. The convenience constructor uses `page` and `data`. Previous/next may be null at boundaries; identifiers may represent numbers or cursors. The wrapper must be nonblank and cannot contain a dot.

Wrap it with `Prop.scroll(page)` or compute it through `Prop.scrollWith(task)`. Use a relative `matchOn` path such as `data.id` for stable item identity. Supplying a non-ScrollPage result to a scroll source fails rather than producing fabricated pagination metadata.

This Java DTO does not promise all fields or collection helpers of Rust's length-aware paginator. Build any application-specific total/count/link DTO explicitly.

## Observe Feed

The example's `/feed?page=1` returns three rows and next-page metadata. The official scroll component can append later pages or prepend earlier ones; merge intent comes from the request. Reset replaces the held collection and suppresses the previous merge behavior.

Keep page/cursor validation in the application. The demo rejects pages outside 1–3 with a safe 400 Error Page. Real cursor pagination should enforce authorization and stable ordering for every fetch, including requests made after another tab or data change.

## Verify boundaries

Test first/last pages, append, prepend, reset and duplicate matching. Inspect the Page's wrapper/data and pagination metadata, then the actual order of browser rows. Null previous/next is a boundary instruction, not an error or a demand to fetch page zero.

Core parity cases cover numeric/cursor/wrapper/deferred combinations. Browser tests cover the demo's real official-client prepend/append/reset flow. They do not qualify your database consistency under concurrent writes. Use [merging](merging.md) for identifier semantics and [deferred props](deferred.md) if pagination is loaded after initial content.
