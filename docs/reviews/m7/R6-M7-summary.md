# R6 CodeRabbit Review Summary — M7

## Review Metadata

- Scope: M7-only committed diff
- Base: `e31f9e2b48caeaa3d6faae995cf56515226605a7`
- Target: `ec3644912521275dd6f413b4b4a64eb9a0ce1b50`
- Current-state comparison: `83276cfa1920a2fc01c1d838a982ccc99103843f`
- CodeRabbit CLI: `0.7.0`
- Result: `review_completed`
- Reviewed files: 61
- Raw findings: 15 (`2 critical`, `9 major`, `4 minor`)
- CLI errors in completed artifact: 0
- Raw artifact SHA-256:
  `fd74c37b919a6745993a92ba208483243f8956ba2c1e3a1e866a1afabab55a02`

The first attempt stopped before analysis with a recoverable eight-minute organization rate limit.
That incomplete stream was moved to `/tmp` and is not review evidence. The unchanged retry completed
successfully in an isolated local clone. No build, test, container, migration, or application
command was run.

## M7 Human Triage

| CR severity | Location | Disposition | Current status |
|---|---|---|---|
| Major | `ImageAssetRepository` mutation and cleanup scope | **Confirmed, downgraded to Minor.** `persist`, `remove`, and `garbage` omit the explicit `OwnerId` required for user-content repositories. Current API callers obtain assets through owner-scoped lookups and cleanup is internal, so no reachable cross-owner API path was found. Require and validate the scope at mutation boundaries; model system-wide cleanup explicitly rather than bypassing the rule. | Still present. |
| Major | `ImageAssetRepository.garbage()` | **Confirmed, downgraded to Minor.** It limits the oldest candidates to 100 and only then performs per-row reference queries and filtering. Referenced rows can reduce or indefinitely starve an eligible cleanup batch, while also creating an N+1 query. Put both `NOT EXISTS` predicates in the bounded query and test a blocked-prefix dataset. | Still present. |
| Major | `compose.yaml` S3 encryption | **Confirmed.** The application and deployment guide support `GLACIER_S3_SERVER_SIDE_ENCRYPTION`, but Compose does not pass it to the container. An operator can request `AES256` or `aws:kms` and silently receive no application-requested encryption. | Still present. |
| Minor | Image-setting audit metadata | **Confirmed.** A combined lifecycle/image settings update is recorded only with `area=user-lifecycle`, making image-limit and quota changes inaccurately classified. No pre-existing combined identifier was found, but the metadata still needs a canonical instance-settings classification. | Still present and now covers additional M10 settings too. |
| Major | `ImageService` storage/database mutation boundary | **Confirmed.** Object storage writes and deletes occur outside the database transaction's durable state. A late commit failure or a partial main/thumbnail operation can leave untracked objects or metadata pointing to missing bytes; quiet cleanup is not retryable or observable. Introduce durable, idempotent pending operations/reconciliation and inject failures at each boundary for all backends. | Still present. |
| Minor | Filesystem `probeContentType()` null | **False positive.** `StoredObject.contentType` has no consumer. Image downloads use the owner-scoped MIME type stored in `image_assets`, and portable transfer reads only the stream/length. A null filesystem probe does not reach a response or contract field. | No failing path exists. |
| Major | S3 client timeouts | **Confirmed.** The synchronous SDK client has no bounded API-call and attempt timeouts. S3 upload/download, health, deletion, and cleanup calls can hold request or scheduler threads for provider/network retry durations. Configure and test explicit bounds. | Still present. |
| Major | PostgreSQL binary-load connection cleanup | **Confirmed.** `loadDatabase()` transfers resources to the returned stream only on success, but leaks its connection and possibly statement/result set when preparation, execution, or stream acquisition throws. Repeated storage failures can exhaust the pool. | Still present. |
| Minor | README development image-root export | **False positive.** The exported environment value is a higher-priority configuration source and is also equal to the dev-profile default. Keeping it explicit is harmless and documents the selected disposable path. | Still valid documentation. |
| Minor | `.env.example` image variables | **Partially confirmed; duplicate of the Compose S3 finding.** The S3 encryption variable is missing and should be added with the Compose pass-through. The filesystem root is intentionally fixed to the persistent container volume and is not a supported Compose pass-through, so adding only that template entry would be misleading. | S3 entry remains missing; no separate concern is counted. |
| Critical | `V6__image_assets.sql` missing settings columns | **False positive.** V1 already creates `allowed_upload_types`, `maximum_image_bytes`, and `per_user_storage_quota_bytes`. M7 maps them in `InstanceSettingsEntity`, reads them in `ImageService`, and updates them through `AdminSettingsUpdate`; V6 correctly adds only the new orphan-grace column. | Schema and persistence path remain present. |
| Major | Image lightbox modality | **Confirmed.** The nested `role=dialog` sets `aria-modal=true` without moving/trapping focus or making the editor background inert. Keyboard and screen-reader navigation can remain behind the overlay. Use a native modal or implement focus entry, containment, restoration, and background exclusion. | Still present. |
| Major | Concurrent upload state indexes | **Confirmed, downgraded to Minor.** Progress, completion, and errors identify uploads by captured array index. Overlapping picker/drop/paste batches can remove an earlier row and shift later indexes, leaving progress or errors attached to the wrong row or a completed row stuck. Stable IDs are required. | Still present. |
| Critical | Markdown image sanitizer | **False positive.** Raw HTML is escaped by `renderer.html`; `renderer.image` emits an image only for the owned UUID scheme and rejects external Markdown images; the URI expression excludes `javascript:` and `data:`. Existing tests cover raw HTML, dangerous URLs, and external images. A narrower hook is defense in depth, not evidence of a Critical bypass. | The containment path remains intact. |
| Major | Missing M7 request-body limit | **Confirmed.** At M7, only `max-form-attribute-size=40M` is configured; the lower overall HTTP body limit can reject uploads before the 40 MB image-processing envelope can downscale them. Configure and test the full request limit, accounting for multipart overhead. | The exact reachability issue is superseded on current `main` by M9's 1536 MB transfer limit; current architecture needs endpoint-specific image limits instead of restoring a global 40 MB cap. |

Eleven raw findings are confirmed or partially confirmed and four are false. The `.env.example`
finding duplicates the Compose S3 pass-through, leaving ten distinct concerns. After human severity
adjustment, six are Major and four are Minor. Both reported Criticals are false positives; no
Blocker or Critical issue is confirmed.

## R0 Carry-Forward Validation

| R0 finding | At M7 target and current `main` |
|---|---|
| Contributor guide generated-client path | Persists without the `frontend/` prefix. |
| Initialization loses diagnostics/correlation ID | The exact ping callback is superseded, but setup-status startup errors still discard the safe problem body and correlation ID. |
| Generic browser title | Persists as `Frontend`. |
| Unsupported `ng e2e` documentation | Persists in `frontend/README.md`. |
| Publishing script stages caller tree | Persists in generated `git_push.sh`. |
| Note save lacks explicit `OwnerId` | Contract persists with reduced production reachability; M7 repeats the design issue in its image repository. |
| Notebook save lacks explicit `OwnerId` | Persists with reduced production reachability. |
| Generated TSV separator is spaces | Persists. |
| `OwnedEntityId.equals` is null-unsafe | Persists. |
| Publishing script embeds token in remote URL | Persists. |
| Generated repository URL is placeholder | Persists. |
| Generated array `toString()` is incorrect | Persists with dormant runtime impact. |
| Generated Angular package metadata mismatch | Persists for standalone-package use. |
| Global OpenAPI security requires CSRF on reads | Persists across the new image GET operations. |
| Generated package omits `@angular/common` peer | Persists. |

Fourteen findings remain directly applicable. The superseded ping implementation still leaves its
safe-diagnostic requirement unresolved, so no R0 remediation group is closed.

## R1 Carry-Forward Validation

| R1 finding | At M7 target and current `main` |
|---|---|
| Duplicate bootstrap-token delivery | Persists. |
| Missing negative `failure_count` migration test | Persists. |
| Duplicate schema-test finding | Remains a duplicate. |
| Whitespace-only secrets accepted | Persists; M7's added secret test does not change `SecretProvider.nonBlank`. |
| Missing setup error/rate-limit UI tests | Persists. |
| Invalid `app.css` shorthand | Remains resolved since M3. |
| Invalid setup stylesheet shorthand | Persists. |
| Management bind finding | Remains rejected for the documented internal-container deployment. |
| Generic 4xx problem mapping | The new image mapping is specific; remaining generic 4xx statuses are still misclassified. |
| Unhandled exception logging loses stack trace | Persists. |

Seven of R1's eight distinct confirmed concerns remain relevant.

## R2 Carry-Forward Validation

| R2 finding | At M7 target and current `main` |
|---|---|
| Session GETs inherit CSRF | Persists; duplicate of R0. |
| Successful session responses omit correlation header in OpenAPI | Persists in the contract; runtime still supplies it. |
| Claimed M3 migration omissions | Remains false. |
| CI lacks invalid-CSRF logout smoke | Persists. |
| Malformed public URL has unclear exception | Persists. |
| Claimed DNS lookup from user login input | Remains false. |
| Admin status load can hang | Persists; M7 adds image status fields but no error handler. |
| Session restore races initial navigation | Persists. |
| Login fields omit validation feedback | Persists. |

All seven confirmed R2 findings remain relevant; both false positives remain rejected.

## R3 Carry-Forward Validation

| R3 finding | At M7 target and current `main` |
|---|---|
| Authenticated SMTP can use `/dev/null` password fallback | Persists. |
| Entrypoint strips all trailing password whitespace | Persists. |
| Reset and invitation bearer-token inputs are cleartext | Both persist; one remediation group. |
| Reset submission permits concurrent requests | Persists. |
| Password-reset neutrality test gap | Persists. |
| Lifecycle email token assertion gap | Persists. |
| Admin settings can save unloaded defaults | Persists and is more consequential after M7 adds image limits, quota, types, and grace settings. |
| User deactivation leaves stale UI | Persists. |
| Initial user-load errors are hidden | Persists. |
| Admin component test gap | Persists; no admin `.spec.ts` files exist. |
| Nullable allowed-email-domain claim | Remains false due to the non-null database column/default. |
| OpenAPI username patterns | Persist. |

All twelve confirmed raw R3 findings remain relevant; combining the two token-input findings leaves
eleven distinct concerns.

## R4 Carry-Forward Validation

| R4 finding | At M7 target and current `main` |
|---|---|
| Per-ID note page loading | Confirmed Minor persists; M7's repository additions do not change `notes()`. |
| Unbounded note content and 10,000-item checklist payloads | Confirmed Major persists through the expanded OpenAPI contract. |
| Claimed null-list failure in `updateNote` | Remains false because generated required fields are validated before the service. |

Both confirmed R4 findings remain relevant, and the false positive remains rejected.

## R5 Carry-Forward Validation

| R5 finding | At M7 target and current `main` |
|---|---|
| Dialog prototype mocks leak across tests | Confirmed Minor persists; M7 only adds fixture fields. |
| Handled initialization error is rethrown | Confirmed Minor persists. |
| Stale `loadMore()` can append to a newer view | Confirmed Major persists. |
| Note-card actions silently discard errors | Remains false; store methods still report their own failures. |
| Multiline code action creates a malformed closing fence | Confirmed Minor persists. |

All four confirmed R5 findings remain relevant; the one false positive remains rejected.

## Recommended Follow-up

1. Design the durable, idempotent storage-operation and reconciliation protocol before accepting
   filesystem, PostgreSQL, or S3 mutation reliability.
2. Pass through S3 encryption, bound SDK calls, and close PostgreSQL resources on every failure path.
3. Enforce owner scope in image mutations and move reference exclusion into the bounded garbage
   query.
4. Fix modal focus behavior and stable upload identities with keyboard and overlapping-upload tests.
5. Add backend-specific failure injection, body-boundary, cleanup-starvation, and concurrency tests,
   then retain the unresolved R0–R5 Major groups in the remediation backlog.

## Batch 4 Remediation Update

Revalidated on 2026-07-24 against base `c28f701`.

| Original finding | Final status |
|---|---|
| Image-setting audit metadata is misclassified | **Resolved.** Instance-settings changes now use the canonical `instance-settings` audit area, with resource-level regression coverage. |

Implementation and full-suite evidence is in the
[Batch 4 record](../remediation/batch-4/B4-PERSISTENCE-CONTRACTS-summary.md).
