# R1 CodeRabbit Review Summary — M2

## Review Metadata

- Scope: M2-only committed diff
- Base: `9933582e0eef0aec0a5f090cfaa127b4b1c73c18`
- Target: `b13b862b50ee6c561792fda9b324e4366e191ed0`
- Current-state comparison: `663cd084a1505504de9a47287e06aa1b8f736042`
- CodeRabbit CLI: `0.7.0`
- Result: `review_completed`
- Reviewed files: 53
- Raw findings: 10 (`7 major`, `3 minor`)
- CLI errors: 0
- Raw artifact SHA-256:
  `d38b552485b7b22bc0d24f3abde8f09109f04bfb3316bc6cd31b42d79e6a0d4c`

The review ran in an isolated local clone against the exact M2 range. No build, test, container,
migration, or application command was run. Validation below is based on source and specification
inspection.

## M2 Human Triage

| CR severity | Location | Disposition | Current status |
|---|---|---|---|
| Minor | `openapi/glacier-notes-v1.yaml` | **Confirmed.** The bootstrap token is modeled as both a security scheme and an explicit header. The generated client sets the explicit value and can then overwrite it from configured credentials. Keep one delivery mechanism and regenerate. | Still present. |
| Major | `V2__bootstrap_rate_limits.sql` | **Confirmed, downgraded to Minor.** The constraint already rejects negative counts; the missing item is a behavioral migration test. This is the canonical instance of the issue. | Test gap still present. |
| Major | `DatabaseSchemaTest.java` | **Duplicate** of the preceding migration-test finding. | Test gap still present. |
| Major | `SecretProvider.java` | **Confirmed.** `nonBlank` accepts whitespace-only values. M2 later rejects them during first-run validation, limiting immediate impact; current session-token code only enforces length, making central validation important. | Still present and now security-relevant. |
| Major | `setup.component.spec.ts` | **Confirmed, downgraded to Minor.** Validation-error rendering and `429` retry messaging are implemented but lack regression coverage. | Test gap still present. |
| Minor | `app.css` | **Confirmed.** `font: 700 0.9rem / 1 inherit` is an invalid shorthand because `inherit` cannot be combined as a family value. | Resolved incidentally when the rule was replaced after M2. |
| Minor | `setup.component.css` | **Confirmed.** It contains the same invalid shorthand. | Still present. |
| Major | `application.properties` | **Rejected for the supported deployment.** The container listens on its internal interface while Compose publishes management on host loopback by default. The specification explicitly allows an internal container-network bind. Binding only container loopback would break the documented host health probe. | Deployment remains internally bound by default; public exposure is documented as unsupported. |
| Major | `ProblemExceptionMapper.java` fallback | **Confirmed.** Generic non-404 `WebApplicationException` responses receive `INTERNAL_ERROR` and misleading resource text. Use a client-error code/detail for remaining 4xx statuses. | Partially improved for `401`/`403`; still present for other generic 4xx statuses. |
| Major | `ProblemExceptionMapper.java` logging | **Confirmed.** The unhandled exception is not passed to `LOG.errorf`, so the stack trace is lost. | Still present. |

Result: 8 distinct confirmed concerns, 1 duplicate raw finding, and 1 contextually invalid finding.
There is no newly identified Blocker or Critical issue. The highest-priority current concerns are
central secret validation and accurate/diagnosable problem mapping.

## R0 Carry-Forward Validation

Only `frontend/src/app/app.ts` and `openapi/glacier-notes-v1.yaml` among the R0 finding locations
changed in M2. The other locations were byte-for-byte unchanged across the M2 range.

| R0 finding | At M2 target | At current `main` |
|---|---|---|
| Contributor guide generated-client path | Persists unchanged | Persists unchanged |
| Ping failure drops safe diagnostics/correlation ID | Persists in the M2 ping callback | Exact ping path removed; setup-status errors remain generic and `ProblemService` still omits `correlationId` |
| Generic browser title | Persists unchanged | Persists unchanged |
| Unsupported `ng e2e` documentation | Persists unchanged | Persists unchanged |
| Generated publishing script stages caller tree | Persists unchanged | Persists unchanged |
| Note save lacks explicit `OwnerId` | Persists unchanged | Persists unchanged |
| Notebook save lacks explicit `OwnerId` | Persists unchanged | Persists unchanged |
| Generated TSV separator is spaces | Persists unchanged | Persists unchanged |
| `OwnedEntityId.equals` is null-unsafe | Persists unchanged | Persists unchanged |
| Publishing script embeds token in remote URL | Persists unchanged | Persists unchanged |
| Generated repository URL is placeholder | Persists unchanged | Persists unchanged |
| Generated exploded-array `toString()` is incorrect | Persists unchanged; runtime impact remains dormant | Persists unchanged; runtime impact remains dormant |
| Generated Angular package metadata is mismatched | Persists unchanged; standalone-package impact | Persists unchanged; standalone-package impact |
| Global OpenAPI security requires CSRF on reads | Carried forward despite M2 contract additions | Still present |
| Generated package omits `@angular/common` peer | Persists unchanged | Persists unchanged |

Fourteen of fifteen R0 findings remain directly applicable. The ping-specific finding is
structurally superseded on current `main`, but its underlying correlation-ID visibility requirement
is not resolved. Therefore none of the R0 remediation groups should be closed based on the M2 or
current-state inspection.

## Recommended Follow-up

1. Track the two owner-scoped repository contracts and the generated publishing-script risks as
   existing R0 work, not new M2 issues.
2. For M2, prioritize central secret validation and the two problem-mapper defects.
3. Correct the canonical bootstrap-token and global security definitions before regenerating code.
4. Add focused schema and setup-error tests, then record all executed verification in
   `test-results.md`.

## Batch 5 Remediation Update

Revalidated on 2026-07-24 against base `7514d1b`.

| Original finding | Final status |
|---|---|
| Setup validation/API/rate-limit feedback lacks regression coverage | **Resolved.** Behavioral tests cover invalid fields, associated messages, server details, and throttling feedback. |
| Setup stylesheet contains invalid `font` shorthand | **Resolved.** The action now inherits the font and declares weight and line-height with valid properties. |

Focused pre-fix failures and full verification are in the
[Batch 5 record](../remediation/batch-5/B5-FRONTEND-ACCESSIBILITY-summary.md).
