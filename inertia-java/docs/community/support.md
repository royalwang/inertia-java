---
title: "Getting help and reporting bugs"
description: "Provide a minimal reproducible example with versions/behavior; avoid sensitive payloads."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/README.md
  - inertia-java/docs/getting-started/compatibility.md
verification:
  - inertia-java/scripts/verify.mjs
---

# Getting help and reporting bugs

Use a minimal sanitized reproduction that identifies the owning layer. Inertia Java is currently documented as `0.1.0-SNAPSHOT`; the documentation does not promise a published stable artifact or support SLA.

## Include useful evidence

Provide the library/source revision, Java/Boot/Node and official-client versions, operating system, SSR/CSR mode and exact commands. State expected/actual behavior and the smallest controller/configuration or reproducible application that shows it. Include HTTP status and relevant sanitized protocol headers, browser console/network failure and the first actionable error.

For SSR, include build/root identities and the fallback category. For session problems, include the sequence of requests, bag/namespace and whether identity was invalidated; never include actual cookies, tokens or personal Page payloads. For deployment, state whether evidence is local rehearsal or the real proxy/host.

## Choose a current channel

The canonical repository is [royalwang/inertia-java](https://github.com/royalwang/inertia-java). Use its currently enabled public contribution/discussion facilities for non-sensitive reproductions. The repository is a fork and a public Issues tab was not visible during documentation preparation, so this guide does not advertise an assumed `/issues/new` endpoint. A focused pull request with a failing reproduction is suitable when you can propose a change.

No separate support email, forum, paid support agreement or response deadline is declared here. Maintainers can add approved channels as repository settings evolve.

## Before posting

Search existing documentation and available public reports. Try the matching [troubleshooting guide](../troubleshooting/startup.md) and verify the toolchain/build inputs. Keep logs minimal and scrub secrets. For a suspected vulnerability, follow [security reporting](security.md) and do not publish exploit details or a sensitive reproduction through public review.

A report is not complete merely because a screenshot shows an error. Reproducible inputs, expected behavior and a named failure boundary make investigation possible.
