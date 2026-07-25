# Known issues

## npm advisories

CI runs `npm audit --omit=dev --audit-level=high` against the frontend. The one remaining advisory,
if still reported, is a low-severity Windows-only Vite development-server issue in a transitive
esbuild version; production artifacts do not contain the development server.

## Dependency and image scan suppressions

`backend/owasp-suppressions.xml` suppresses CVSS ≥ 7.0 findings against Java artifacts that Quarkus
3.37.3 pulls in transitively (not direct dependencies of this project). Each is a case where the
vulnerable code path is unreachable in this codebase rather than a patched/fixed version being
unavailable. Suppression is matched by CPE (not just `packageUrl`) because dependency-check
correlates each CVE against every jar sharing that CPE, and which specific sibling jar gets flagged
has varied between scan runs:

- **CVE-2026-44891, CVE-2026-55831, CVE-2026-55833** (Netty, CPE `netty:netty`, observed against
  both `netty-transport` and `netty-transport-classes-epoll`, via `quarkus-vertx`) — in Netty's STOMP
  and SPDY frame decoders; this application never constructs a STOMP or SPDY server/client, only
  HTTP/1.1 and HTTP/2 through the Quarkus Vert.x HTTP server.
- **CVE-2026-29181, CVE-2026-39883, CVE-2026-39882, CVE-2026-41178** (`opentelemetry-semconv`, CPE
  `opentelemetry:opentelemetry`, via Quarkus's OpenTelemetry/Micrometer integration) — false
  positive: these CVEs are against "OpenTelemetry-Go", a different language ecosystem's
  implementation; this project has no Go dependencies. The specific CVE IDs returned against this CPE
  have shifted between scan runs without the jar version changing; all IDs observed so far are
  suppressed, and the list should be pruned to what the NVD dataset currently returns the next time
  this file is revisited.
- **CVE-2026-15075, CVE-2026-15076** (Vert.x, CPE `eclipse:vert.x`, observed against both
  `vertx-core` and `vertx-auth-common`, via `quarkus-virtual-threads`) — in Vert.x's Web Client
  (`WebClient`/`WebClientSession`) redirect and cookie-domain handling; this backend makes no
  outbound requests through `vertx-web-client` and uses `vertx-core`/`vertx-auth-common` only for the
  inbound HTTP server.

`CVE-2026-54515` (`jackson-databind`, transitive via Quarkus's Jackson integration) was also flagged
by the M12 scan but is CVSS 5.3 (below the 7.0 build-failure threshold), so it is not suppressed and
remains visible only in the generated HTML/JSON report artifact.

All suppressions are scoped to specific CVE IDs (not the whole dependency) and should be revisited
the next time the Quarkus platform version changes, in case the bundled transitive versions change.
If a future OWASP dependency-check or Trivy finding is suppressed, record it here with the CVE
identifier, the reason it does not apply, and the date it was added, in addition to the justification
required in the suppression file itself.
