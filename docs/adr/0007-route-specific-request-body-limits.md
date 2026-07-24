# ADR 0007: Route-specific request-body limits

- Status: Accepted
- Date: 2026-07-24

## Context

Portable imports can be much larger than ordinary JSON or image requests. Quarkus exposes a global
HTTP body ceiling and a global multipart-part ceiling, so configuring both for the transfer maximum
would let every API endpoint buffer an unnecessarily large request.

## Decision

Keep the Quarkus limits as an absolute transfer-sized ceiling and install an early Vert.x body
handler for API requests. Ordinary request bodies receive a 10 MiB limit. Image uploads and portable
imports receive their configured file maximum plus a 1 MiB multipart-envelope allowance. The same
handler applies to fixed-length and chunked requests and emits a correlation-aware
`application/problem+json` response when the selected limit is exceeded.

Startup validates that every limit is positive and that the absolute ceiling covers the ordinary,
image, and transfer limits. Operators who raise the portable-import maximum must raise the absolute
ceiling by at least the multipart allowance.

## Consequences

Large portable imports remain supported without extending their resource exposure to ordinary API
operations. The route-specific handler owns upload spooling before Quarkus REST consumes the body,
so its upload directory must remain aligned with the Quarkus upload-directory setting.
