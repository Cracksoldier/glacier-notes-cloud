# Batch 4 Persistence and Contract Remediation

## Scope

- Base commit: `c28f701`
- Original findings: R0/M0–M1, R2/M3, R3/M4, R4/M5, R6/M7, R7/M8, and R8/M9
- Milestone gates: explicit owner scope, owner-safe pagination, bounded note payloads, consistent
  search maintenance/history hashes, typed transfer contracts, and audited administrative settings
- CodeRabbit mode: one `--uncommitted` review of the finished implementation diff

The original findings were re-read against their milestone acceptance criteria and verified on the
Batch 4 base before code changes. Regression tests were added and observed failing before each
production fix. Detailed commands and outcomes are in [test-results.md](test-results.md).

## Original Finding Resolution

| Review | Finding | Root cause and resolution | Final status |
|---|---|---|---|
| R0/M0–M1 | Note/notebook saves omit explicit owner scope | Dormant repository adapters inferred scope from the entity. Their contracts now require `OwnerId`, reject mismatches, and key persistence by the supplied scope. | Resolved |
| R0/M0–M1 | `OwnedEntityId.equals` dereferences null JPA fields | Equality assumed fully hydrated identifiers. It now uses null-safe component equality. | Resolved |
| R2/M3 | Successful session responses omit correlation headers | Five successful responses lacked the shared OpenAPI header. The canonical contract now declares it consistently. | Resolved |
| R2/M3 | Malformed public URL leaks `IllegalArgumentException` | URI parsing was outside the configuration-failure path. Malformed values now produce a clear `IllegalStateException`. | Resolved |
| R3/M4 | Admin/invitation usernames omit the canonical pattern | Two request schemas had length constraints only. They now share the accepted username character pattern. | Resolved |
| R4/M5 | Note pages perform one entity lookup per selected ID | Native pagination was followed by per-row `find`. Owner-scoped batch loading now uses one second query and restores ranked/page order. | Resolved |
| R4/M5 | Note content and checklist counts are excessively permissive | The contract had unbounded content and 10,000 checklist entries. Content is capped at 8 MiB and checklists at 1,000 items; existing 10,000-character item text remains bounded. | Resolved |
| R6/M7 | Settings audit metadata is misclassified | Instance-settings updates used the legacy `user-lifecycle` area. Audit metadata now records `instance-settings`. | Resolved |
| R7/M8 | Legacy version hashes remain nullable | V7 allowed existing null hashes. V11 deterministically backfills payload hashes and enforces `NOT NULL`; an isolated V10-to-V11 upgrade test preserves history. | Resolved |
| R7/M8 | Checklist relocation refreshes only the new note | The update trigger ignored the old note key. It now recomputes both affected search documents. | Resolved |
| R8/M9 | Export scope/resource invariant is incomplete | Runtime-only validation allowed invalid direct entity/SQL writes. OpenAPI 3.1, the factory, and the database constraint now express the same invariant. | Resolved |
| R8/M9 | Portable base64 length is hard-coded | Parser capacity ignored configured decoded-image limits. Checked base64 expansion now derives the encoded ceiling and rejects overflow at startup. | Resolved |
| R8/M9 | Import/export resources return internal records as `Object` | Resource signatures bypassed generated DTO drift checks. User and admin paths now share a `TransferJob` mapper and typed generated request/response models. | Resolved |
| R8/M9 | Export download can leak a stream during metadata failure | The stream opened before file size was known. Metadata is resolved first and response-build failures close the owned stream. | Resolved |

## Finished-Diff Review

CodeRabbit CLI 0.7.0 completed one uncommitted review against base `c28f701`. It reviewed 21 files
and reported four findings. The immutable [raw JSONL](B4-PERSISTENCE-CONTRACTS.jsonl) contains 13
records, ends with `review_completed`, and has SHA-256
`25a8e1120ebb0f724fffd1df134424554a1bccc683c89c30a4a4decb2ba052e0`.

| Severity | Finding | Classification and final status |
|---|---|---|
| Major | The image base64 allowance widened the global JSON string limit. | Valid, resolved. General strings retain the configured cap while image base64 is decoded through a size-limited stream. Malformed and oversized payload tests pass. |
| Minor | The checklist-relocation test did not prove that exactly both notes were refreshed. | Valid, resolved. The test now compares the complete two-row result map. |
| Minor | The export-scope test asserted migration text rather than database behavior. | Valid, resolved. Valid `ALL`, `NOTEBOOK`, and `NOTE` inserts succeed; invalid identifier combinations are rejected by PostgreSQL. |
| Minor | Identifier equality coverage relied only on two empty reflective instances. | Valid, resolved. A persisted and reloaded entity now proves matching equality/hash behavior and differing-ID inequality; null-safe hydration coverage remains. |

No finding was rejected or deferred. Per the one-review rule, CodeRabbit was not run again after
these four valid comments were addressed.
