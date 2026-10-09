# Inertia Java security reporting

This policy covers the development version `0.1.0-SNAPSHOT`. A stable security support and backport window has not yet been declared.

## Private reporting

Report vulnerabilities through [GitHub private vulnerability reporting](https://github.com/royalwang/inertia-java/security/advisories/new). Sign in to GitHub to submit a private report. Do not publish exploit details, production credentials, cookies, tokens or personal Page data in public issues or pull requests.

Include the affected version or revision, prerequisites, impact, a minimal sanitized reproduction and any known mitigation. Use synthetic data rather than production credentials or user datasets.

## Application responsibilities

Applications own authorization, CSRF protection, cookie and TLS policy, trusted-proxy configuration, session storage and renderer access controls. See the [security guide](docs/community/security.md) for the relevant integration boundaries.
