# Dependency and license inventory

Direct foundation dependencies are pinned in `backend/pom.xml`, `frontend/package.json`, and
`frontend/package-lock.json`. Transitive versions are resolved reproducibly by the Quarkus BOM,
Maven Wrapper, and npm lockfile.

| Component | Version | License |
|---|---:|---|
| Quarkus | 3.37.4 | Apache-2.0 |
| OpenAPI Generator | 7.24.0 | Apache-2.0 |
| PostgreSQL JDBC | Quarkus BOM | BSD-2-Clause |
| Flyway Community | Quarkus BOM | Apache-2.0 |
| TwelveMonkeys ImageIO WebP | 3.13.1 | BSD-3-Clause |
| AWS SDK for Java S3 | 2.41.32 | Apache-2.0 |
| Angular | 22.0.7 | MIT |
| RxJS | 7.8.x locked | Apache-2.0 |
| TypeScript | 6.0.x locked | Apache-2.0 |
| Vitest | 4.1.10 | MIT |
| Biome | 2.5.4 | MIT OR Apache-2.0 |
| Font Awesome Free | 7.3.1 | CC-BY-4.0 AND MIT AND OFL-1.1 |
| DOMPurify | 3.4.12 | MPL-2.0 OR Apache-2.0 |
| Marked | 18.0.6 | MIT |
| Playwright | 1.55.0 | Apache-2.0 |

Review the complete resolved trees with `./mvnw -pl backend dependency:tree` and `npm ls --all`
from `frontend`. CI also runs npm audit; see `docs/KNOWN_ISSUES.md` for the one remaining advisory.

## Backend vulnerability scanning

CI runs `org.owasp:dependency-check-maven` against the backend module after `./mvnw verify`,
failing the build on any finding at CVSS 7.0 (High) or above. Run it locally with:

```bash
./mvnw -pl backend org.owasp:dependency-check-maven:check
```

The plugin works without credentials but is faster and more reliable with an NVD API key exported
as `NVD_API_KEY` (also read from the `NVD_API_KEY` GitHub Actions secret in CI, if configured).
Reports are written to `backend/target/dependency-check-report` and uploaded as a CI artifact on
every run. The Sonatype OSS Index analyzer (a supplementary, non-NVD data source) is disabled via
`ossindexAnalyzerEnabled=false`, because anonymous requests to that service are aggressively rate
limited and turn transient `401 Unauthorized` responses into a hard scan failure unrelated to any
actual vulnerability; NVD remains the scan's primary and sufficient CVE source.

If a finding is a false positive or genuinely unreachable in this application (e.g. the vulnerable
code path is never invoked), add a suppression to `backend/owasp-suppressions.xml` with a `notes`
element explaining why, following the
[suppression file schema](https://jeremylong.github.io/DependencyCheck/general/suppression.html).
Do not suppress a finding solely to unblock CI without recording the justification — suppressions
receive the same review scrutiny as any other change to a security gate.
