---
title: "Append, prepend and deep merge"
description: "Use matchOn with actual client state, update IDs and reset merged state."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/inertia-core/src/main/java/io/inertia/core/Prop.java
  - inertia-java/inertia-core/src/main/java/io/inertia/core/PropsResolver.java
  - inertia-java/examples/spring-react/src/main/java/io/inertia/example/AdvancedDemo.java
verification:
  - inertia-java/inertia-core/src/test/java/io/inertia/core/AdvancedPropsTest.java
  - inertia-java/examples/spring-react/frontend/e2e/advanced.spec.ts
---

# Append, prepend and deep merge

Merge APIs describe how the official client combines a matching partial response with its current state. Java emits metadata; it does not retain a browser's merged collection on the server.

## Choose the instruction

| API | Intent |
| --- | --- |
| `.merge()` | Append a root collection |
| `.prepend()` | Prepend a root collection |
| `.deepMerge()` | Recursively merge object state |
| `.appendAt(path)` / `.prependAt(path)` | Combine a collection at an inner path |
| `.matchOn(path)` | Match collection items by a relative identifier path |

Paths are relative to the prop. Define merge options before `matchOn`; otherwise it fails. Modifier composition is not uniformly additive: choosing another mode can replace prior options. In particular, do not pile incompatible root/deep/path modes together and assume they all survive.

## Observe a delta

The Advanced example declares `profile` with deep merge and `matchOn("members.id")`. A later partial response changes theme, updates member 2 and adds member 3 without repeating unchanged fields. The client preserves name/language and avoids duplicating matching members.

Use the reset action to request replacement. `X-Inertia-Reset` suppresses merge metadata for the reset path. A normal full visit should contain a complete coherent snapshot rather than relying on another browser's previous delta history.

## Handle identity and deletion deliberately

Merge is state-combination behavior, not authorization, persistence or a deletion protocol. Ensure identifiers are stable and scoped correctly. If a record should disappear or permissions changed, choose an appropriate replacement/reset/navigation flow instead of assuming omission removes it from the client's retained collection.

Verify repeated deltas, matching identifiers, reset and a new document visit. Assert actual browser state as well as server metadata. Core tests cover option/metadata rules; the Advanced browser test proves nested IDs update without duplicates. See [scroll](scroll.md) for direction-specific pagination metadata and [partial reloads](partial-reloads.md) for selection.
