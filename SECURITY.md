# Security Policy

## Supported version

Security fixes are applied to the latest commit on the default branch.

## Reporting a vulnerability

Use GitHub's private vulnerability reporting feature from the repository's
Security tab when it is available. Do not include API keys, access tokens,
private health information, or exploitable details in a public issue. If
private reporting is unavailable, open a minimal issue asking a maintainer to
provide a private reporting channel, without disclosing the vulnerability.

Include affected versions, impact, reproduction conditions, and a suggested
mitigation when possible. Please allow maintainers a reasonable opportunity to
investigate before public disclosure.

## Deployment warning

The repository defaults are intended for local development. A public deployment
must use an origin allowlist, strong database credentials, user authentication, rate
limits, model-usage quotas, restricted management endpoints, TLS, and secret
management. Never enable the `evaluation` Spring profile in production.
