# R7 CodeRabbit Review Summary — M8

## Review Metadata

- Scope: M8-only committed diff
- Base: `ec3644912521275dd6f413b4b4a64eb9a0ce1b50`
- Target: `2cf76f32391581fefe712408b4a08a5181c0dc05`
- Current-state comparison: `e5b7212fcbaa8549c9f0a6f0fce06c5ce7677862`
- CodeRabbit CLI: `0.7.0`
- Result: `review_completed`
- Reviewed files: 40
- Raw findings: 6 (`3 major`, `3 minor`)
- CLI errors in completed artifact: 0
- Raw artifact SHA-256:
  `f16d8b3f24b3734f114be46c78da6e6608c4c2805901515164902035173a4e15`

Two attempts stopped before analysis with recoverable organization rate limits of one minute and
22 seconds. Those incomplete streams remain outside the repository in `/tmp` and are not review
evidence. The unchanged third attempt completed successfully in an isolated local clone checked
out at the M8 target. No build, test, container, migration, or application command was run.

## M8 Human Triage

| CR severity | Location | Disposition | Current status |
|---|---|---|---|
| Major | `V7__search_and_note_history.sql` version hash migration | **Partially confirmed, downgraded to Minor.** `note_versions` has existed since V1, while V7 adds a nullable hash without normalizing old rows. The released application had no pre-M8 writer, which reduces expected production reachability, but an existing null row can still violate M8's hash assumption. Backfill deterministically, enforce the intended nullability contract, and test an upgraded database containing a legacy row. | Still present. |
| Major | `refresh_note_checklist_search()` relocation handling | **Confirmed, downgraded to Minor.** An SQL update that changes `note_id` refreshes only the new note and leaves stale search text on the old note. The JPA entity exposes no note reassignment and normal application writes delete/insert items, so no API path reaches it. The trigger nevertheless declares `UPDATE OF note_id`; refresh both OLD and NEW keys or remove unsupported relocation from the trigger contract. | Still present. |
| Minor | `CoreContentRepository.lastSnapshotAt()` missing-row handling | **False positive.** The scalar query can throw for an absent note, but every caller first obtains the same owner-scoped note, with a lock in mutation paths. The repository method is internal and has no reachable missing-note call path. Returning null would be defensive hardening, not a demonstrated defect. | Caller precondition remains intact. |
| Minor | `CoreContentRepository.latestSnapshotHash()` nullable stream row | **Confirmed as a duplicate of the V7 hash migration concern.** An empty result is handled, but `findFirst()` is unsafe if the newest database value itself is null. Enforcing a non-null migrated schema removes that state; otherwise the query must handle it explicitly. | Still present; not counted separately. |
| Minor | Version-history Escape handling | **Confirmed.** `onKeydown()` closes the lightbox first but does not close the open history panel before invoking the whole-editor close flow. Escape can therefore save/snapshot and dismiss the editor when the user intended to leave history. | Still present. |
| Major | Editor close after snapshot failure | **Confirmed.** Note persistence completes before the secondary history snapshot. If that request fails, the shared `try` skips `closeEditor()` and `refresh()`, leaving an already-saved editor blocked open and making close dependent on history availability. Report snapshot failure independently and always complete the close flow. | Still present. |

Five raw findings are confirmed or partially confirmed and one is false. The repository hash
finding duplicates the migration concern, leaving four distinct concerns: one Major and three
Minor. No Blocker or Critical issue is confirmed.

## Previous-Review Validation

R6 compared all earlier findings with commit `83276cfa1920a2fc01c1d838a982ccc99103843f`.
Between that commit and the R7 comparison commit, Git reports changes only in `docs/reviews/`;
the application, contract, migrations, deployment configuration, and tests are unchanged.
Targeted inspection of the M8 paths and the currently reported M7 paths confirms the following
carry-forward state.

| Review | Revalidation on current `main` |
|---|---|
| R0 — M0–M1 | Fourteen findings remain directly applicable. The old ping implementation is superseded, but setup-status startup failures still discard safe problem details and correlation IDs. No R0 remediation group is closed. |
| R1 — M2 | Seven of eight distinct confirmed concerns remain. The invalid `app.css` shorthand remains fixed; the management-bind claim remains rejected for the documented internal-container topology. |
| R2 — M3 | All seven confirmed concerns remain relevant. The migration-omission and user-input DNS claims remain false. |
| R3 — M4 | All 12 confirmed raw findings remain relevant, representing 11 distinct concerns after combining the reset/invitation cleartext-token inputs. The nullable-domain claim remains false. |
| R4 — M5 | Per-ID note loading and unbounded note/checklist payloads remain relevant. The generated-required-list null claim remains false. |
| R5 — M6 | Dialog mock leakage, rethrown handled initialization errors, stale `loadMore()`, and malformed multiline code fences remain relevant. The silent note-card error claim remains false. M8 extends the stale-pagination path to search without adding request identity to `loadMore()`. |
| R6 — M7 | All ten distinct confirmed concerns remain: image mutation owner scope, garbage-query starvation/N+1 behavior, missing Compose S3 encryption pass-through, inaccurate settings audit classification, non-durable storage/database mutations, unbounded S3 calls, PostgreSQL load cleanup leaks, lightbox focus containment, unstable upload indexes, and request-size boundary design. |

For R6's request-size finding, M9's global `1536M` transfer limit supersedes the exact missing-40M
failure. The underlying concern remains relevant as a need for endpoint-specific image and transfer
limits rather than one permissive global cap. R6's four false positives remain rejected, and the
`.env.example` observation remains a duplicate of the Compose encryption concern.

## Recommended Follow-up

1. Make editor closure independent from history-snapshot availability and add a failed-snapshot UI
   test.
2. Handle Escape as a layered dismissal action: lightbox, history, then editor.
3. Define and enforce the `note_versions.content_hash` upgrade contract, including a legacy-row
   migration test.
4. Correct or narrow the checklist trigger's `note_id` update behavior and test both affected notes.
5. Keep the unresolved Major groups from R0–R6 in the remediation backlog; in particular, do not
   let M8 search pagination obscure the existing stale-`loadMore()` race.
