# R0 CodeRabbit Review Summary — M0 and M1

## Review Metadata

- Source snapshot: repository root commit `9933582`
- Review baseline: synthetic empty commit `71c66ef4ddfe28f2b5e76fade643aa4163f1aafb`
- Synthetic target: `80110097261205e67c0e290e76fea1bda360ce1c`
- CodeRabbit CLI: `0.7.0`
- Result: `review_completed`
- Reviewed files: 113
- Raw findings: 15 (`7 major`, `8 minor`)
- CLI errors: 0 in the completed review
- Raw artifact SHA-256:
  `d91afd3863464b3a4caebe7e3e25687e78f3f9f8b209cd8e4e33d39dee2cee45`

No build, unit test, integration test, migration, Docker, or application command was run as part of
R0. Triage below is based on source inspection and milestone requirements.

## Human Triage

| CR severity | Location | Finding | Disposition |
|---|---|---|---|
| Major | `NoteRepository.java` | `save` accepts only a caller-owned payload and lacks explicit `OwnerId` scope. | Confirmed and still present. Violates the repository-wide owner-scope rule. Add a scope argument and mismatch tests. |
| Major | `NotebookRepository.java` | `save` lacks explicit `OwnerId` scope. | Confirmed and still present. Same boundary issue as notes; track separately because the callers and tests differ. |
| Major | generated `git_push.sh` | Publishing script can stage its current working directory. | Partially confirmed. The command is `git add .`, not `git add ..`, but invoking the script outside its own directory can stage unrelated caller files. Remove the generated script or make its working directory and file scope explicit. |
| Major | generated `git_push.sh` | Token is embedded in the Git remote URL. | Confirmed and still present. Combine with the preceding publishing-script remediation; use credential helpers/`GIT_ASKPASS`, or omit the script. |
| Major | generated `query.params.ts` | Exploded arrays are not repeated by `toString()`. | Confirmed implementation defect, but downgraded to Minor for current runtime because generated services use `toHttpParams()` and no production call to this `toString()` was found. Add generator-level regression coverage. |
| Major | generated `package.json` | Angular 22 package metadata uses mismatched TypeScript/ng-packagr ranges. | Confirmed as standalone generated-package metadata; current application compiles sources with root dependencies, so impact is dormant. Fix generator/template inputs and regenerate. |
| Major | `app.ts` | Ping error drops safe problem diagnostics and correlation ID. | Confirmed for the historical M0 placeholder. Superseded in the current application by later centralized problem handling; verify that behavior in the relevant later milestone review. |
| Minor | `AGENTS.md` | Generated-client path lacks the `frontend/` prefix. | Confirmed and still present. Documentation-only correction. |
| Minor | `index.html` | Browser title is the generic `Frontend`. | Confirmed and still present. User-visible and accessibility metadata correction. |
| Minor | `frontend/README.md` | Documents unsupported `ng e2e` rather than repository scripts. | Confirmed and still present. Documentation correction. |
| Minor | generated `variables.ts` | TSV separator is spaces instead of a tab. | Confirmed and still present. Fix through generator inputs/version; do not edit generated output directly. No current TSV operation was identified. |
| Minor | `OwnedEntityId.java` | `equals` dereferences possibly null JPA fields. | Confirmed robustness issue and still present. Constructor values are non-null, but the protected JPA hydration path can temporarily contain null fields. Use null-safe equality and test it. |
| Minor | generated `package.json` | Repository URL remains a generator placeholder. | Confirmed and still present. Fix generator metadata and regenerate. |
| Minor | `openapi/glacier-notes-v1.yaml` | Global security requires CSRF for read-only operations. | Confirmed and still present. Elevate to Major contract correctness: use session authentication as the read-safe default and declare CSRF explicitly on mutating operations before regeneration. |
| Minor | generated `package.json` | `@angular/common` is absent from peer dependencies. | Confirmed generated-package metadata issue. Combine with the Angular package-version remediation. |

## Consolidated Priorities

### Major remediation groups

1. Make note and notebook saves explicitly owner-scoped and add negative mismatch tests.
2. Remove or harden the generated publishing script so it cannot stage unrelated files or persist
   tokens in remote URLs.
3. Correct the canonical OpenAPI security defaults, annotate mutating operations, and regenerate all
   clients.
4. Correct generated Angular package templates/dependency metadata and verify a standalone package
   build.

### Minor remediation groups

1. Fix exploded-array `toString()` behavior at generator/template level.
2. Make `OwnedEntityId.equals` null-safe.
3. Correct generated TSV and repository metadata through regeneration.
4. Fix the contributor path, browser title, and frontend README.
5. Confirm centralized problem handling retains safe correlation IDs in the current application.

## Recommended Next Step

Before modifying code, create one issue per consolidated group and attach a focused regression test
expectation. Review changes with `cr review --agent --uncommitted`, then run the M0/M1 verification
sequence from `CODERABBIT_M0_M10_REVIEW_PLAN.md`.

## Batch 4 Remediation Update

Revalidated on 2026-07-24 against base `c28f701`.

| Original finding | Final status |
|---|---|
| Note saves lack explicit `OwnerId` scope | **Resolved.** The contract and JPA adapter require the scope and reject entity/scope mismatches. |
| Notebook saves lack explicit `OwnerId` scope | **Resolved.** The contract and JPA adapter require the scope and reject entity/scope mismatches. |
| `OwnedEntityId.equals` is null-unsafe | **Resolved.** Component comparison is null-safe and persisted/reloaded identifier equality and hash behavior are covered. |

Implementation, regression-first, full-suite, deployment, and finished-diff review evidence is in
the [Batch 4 record](../remediation/batch-4/B4-PERSISTENCE-CONTRACTS-summary.md).

## Batch 6 Remediation Update

Revalidated on 2026-07-24 against base `1efb805`.

| Original finding | Final status |
|---|---|
| Both generated `git_push.sh` findings | **Resolved.** The generator cleans stale output and an explicit supporting-file allowlist omits the publisher. |
| Exploded arrays are not repeated by `query.params.ts#toString` | **Resolved.** The canonical template preserves repeated keys and covers scalar and array record shapes. |
| Generated package tooling ranges are incompatible | **Resolved.** Angular 22-compatible TypeScript, ng-packagr, and RxJS ranges are generated and independently compiled in CI. |
| Historical startup error drops safe detail and correlation ID | **Resolved.** The current setup-status failure maps and displays both safe detail and reference. |
| Contributor path, browser title, and frontend README are stale | **Resolved.** Repository-facing metadata now names the correct path, product, and supported commands. |
| TSV separator is spaces | **Resolved.** The canonical generator template emits a tab. |
| Generated repository URL is a placeholder | **Resolved.** Maven generator metadata supplies the repository coordinates. |
| Generated package omits `@angular/common` peer | **Resolved.** The package template includes matching Angular common/core peers. |

Regression-first, full-suite, deployment, and one-pass finished-diff review evidence is in the
[Batch 6 record](../remediation/batch-6/B6-GENERATED-FOUNDATION-summary.md).
