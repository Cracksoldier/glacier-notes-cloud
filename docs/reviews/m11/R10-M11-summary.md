# R10 CodeRabbit Review Summary — M11

## Review Metadata

- Scope: committed M11 diff
- Base: `94ed527361a544a67c9a9f8f82d4819d3419e031`
- Target: `255b7e371aa77b6ba2af08d56262bed20dd3f6dc`
- Command: `coderabbit review --agent --committed --base-commit 94ed527`
- CodeRabbit CLI: `0.7.0`
- Result: `review_completed`
- Reviewed files: 69
- Raw findings: 10 (`8 major`, `2 minor`)
- Raw artifact SHA-256:
  `a19823c2e7b7ae232da17d8843fc198917d19181300a4198bfd537c118b8c267`

The review was run once after the M11 implementation commit. The raw stream contains a terminal
completion event and no error event. Human triage compared every comment with the M11 acceptance
criteria, the committed source, generated-code policy, and earlier review/remediation evidence.

## Finding Classification

| Severity | Finding | Classification | Final status |
|---|---|---|---|
| Minor | Generated `images.service.ts` overload indentation | **Not a new defect.** The upstream Angular generator uses the same four-space method indentation in existing generated services. Generated code is excluded from Biome and must not be edited manually. | No change; generator output retained. |
| Major | `pg_dump` can wait indefinitely | **Valid new finding.** | Resolved with a bounded 30-minute wait, termination, output disposal, and interruption handling. |
| Major | Historical failed jobs make readiness fail | **Valid new finding.** | Resolved: readiness probes current subsystem access; 24-hour failure counts remain on admin status. |
| Major | A stale run can release a newer same-instance lock | **Valid new finding.** | Resolved with a persisted `run_id` fence on acquisition, renewal, and release. |
| Major | Work can outlive its 30-minute lease | **Valid new finding.** | Resolved with run-fenced renewal every 10 minutes and interruption/failure on lease loss. |
| Major | Backup terminal status self-invocation is not independent | **Valid new finding.** | Resolved through a separate CDI service using `REQUIRES_NEW`. |
| Major | Compose backup path ignores overrides and mismatches its volume | **Valid new finding.** | Resolved with one interpolated path for configuration and the volume target. |
| Minor | Filesystem image restore lacks an executable procedure | **Valid new finding.** | Resolved with a Compose helper command that preserves paths and fixes ownership. |
| Major | Four admin mutations and their audit events use separate transactions | **Valid new finding.** | Resolved with shared resource-level transaction boundaries. |
| Major | Audit CSV permits spreadsheet formulas | **Valid new finding.** | Resolved by neutralizing `=`, `+`, `-`, and `@` before CSV escaping. |

Nine valid, M11-specific findings were fixed. The generated indentation comment neither reopens nor
contradicts Batch 6: that remediation covered custom supporting-file templates and package metadata,
not the upstream API-service template. No finding from M0–M10 was reintroduced or changed solely to
expand this review's scope.

## Outcome

Regression tests first demonstrated each valid failure. Focused tests, complete backend and frontend
gates, generated-client packaging, Compose configuration, and a clean backup/restore exercise all
pass. Detailed commands and results are in [test-results.md](test-results.md).
