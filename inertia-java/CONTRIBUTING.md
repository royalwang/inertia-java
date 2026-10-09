# Contributing to Inertia Java

Describe the user-visible behavior, owning module, failure feedback and compatibility impact before implementation. Keep core framework-independent, Spring transport in the adapter and business/authentication examples in the example application.

Use Java 21, the Maven wrapper and locked Node inputs. From this directory run `./mvnw --batch-mode spotless:check verify` for Java changes. Build the example frontend and run affected official-client scenarios for client/SSR behavior. The aggregate verification entry is `node scripts/verify.mjs`; its stages and scope are documented in the [module README](README.md).

For documentation, first build the Maven classifiers with `./mvnw --batch-mode install`, then from `docs/` run `npm ci`, `npm run docs:check`, `npm run docs:build` and `npm run docs:smoke`. Python 3 is required to extract actual Javadoc. Changed canonical examples also require `npm run docs:examples`; the first-application and clean-source quick-start checks exercise real independent builds/browser behavior.

Update the relevant guide, configuration/API reference, canonical example, compatibility matrix and migration notes together when changing a public contract. Generated Javadoc/site output, node_modules, target files and local evidence directories remain outside Git. English is the public documentation's canonical language; all 70 Chinese translations track the SHA-256 of their English Markdown. English edits require semantic review of the translation, then revision updates in its frontmatter and catalog. Checks reject stale revisions, changed executable fences and unindexed Chinese placeholder pages.

Submit a focused pull request to the [canonical repository](https://github.com/royalwang/inertia-java). Explain the final problem/result, verification and limitations. Preserve upstream fixture provenance and third-party attribution. Contributions use [Apache-2.0](LICENSE); do not replace third-party copyright with the Java module's declaration.

Use the [support guide](docs/community/support.md) for sanitized bug reports and [security policy](SECURITY.md) for sensitive findings. No response-time guarantee, public Issues endpoint or undisclosed maintainer team is assumed.
