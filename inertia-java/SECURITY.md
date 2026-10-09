# Inertia Java security reporting

This policy covers the development version `0.1.0-SNAPSHOT`. A stable security support and backport window has not yet been declared.

## Private reporting

A private security reporting channel has not yet been published for this module. Until one is available, keep sensitive details private. Do not publish exploit details, production credentials, cookies, tokens or personal Page data in public issues or pull requests.

When a reporting channel becomes available, include the affected version or revision, prerequisites, impact, a minimal sanitized reproduction and any known mitigation. Use synthetic data rather than production credentials or user datasets.

## Application responsibilities

Applications own authorization, CSRF protection, cookie and TLS policy, trusted-proxy configuration, session storage and renderer access controls. See the [security guide](docs/community/security.md) for the relevant integration boundaries.
