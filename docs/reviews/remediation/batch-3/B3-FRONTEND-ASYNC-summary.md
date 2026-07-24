# Batch 3 Frontend Async Remediation

## Scope

- Base commit: `b5bbb0aeae58e60e18746ddbefaaf383e13e69e6`
- Original findings: R2/M3, R3/M4, R5/M6, R7/M8, R8/M9, and R9/M10
- CodeRabbit CLI: `0.7.0`
- Review mode: one `--uncommitted` review of the finished implementation diff
- Result: `review_completed`; 19 files reviewed; 7 findings
- Raw artifact SHA-256:
  `a4c0cdc8fc70bebe7a6c13a9fd6869d752cbb872bed0f183495c13f70c37b5dc`

The immutable CLI output is [B3-FRONTEND-ASYNC.jsonl](B3-FRONTEND-ASYNC.jsonl). CodeRabbit was run
once, after the original remediation and CI-equivalent checks. Valid findings were addressed and
verified without running a second review.

## Original Finding Resolution

| Review | Finding | Root cause and resolution | Final status |
|---|---|---|---|
| R2/M3 | Initial navigation races session restoration | Guards read an unrestored null session. Restoration is now shared and guards defer their decision until it completes. | Resolved |
| R3/M4 | Admin settings can save unloaded defaults | The form had usable defaults before GET success. Loading/loaded/saving states now disable and guard submission, and every response field is applied. | Resolved |
| R5/M6 | Dialog mocks leak | Prototype replacements lacked descriptor restoration. Specs now restore or delete both properties and destroy fixtures. | Resolved |
| R5/M6 | Initialization rethrows handled errors | `initialize()` reported and rethrew into an unobserved Angular lifecycle promise. It now reports without rethrowing. | Resolved |
| R5/M6 | Stale pagination crosses views/searches | `loadMore()` did not validate request identity. All page continuations now check the active sequence and reset cursor/loading state. | Resolved |
| R7/M8 | Escape closes the editor before history | Dismissal was not layered. Escape now closes lightbox, then history, then editor. | Resolved |
| R7/M8 | Snapshot failure blocks editor close | Snapshot, close, and refresh shared one failure path. Snapshot/refresh failures are reported independently and close completes. | Resolved |
| R8/M9 | Admin import cancellation rejects and stays busy | Cancellation had no catch/finally. It now reports failures, preserves the job on failure, and always clears active busy state. | Resolved |
| R8/M9 | Old transfer polls mutate new operations | A reusable boolean let canceled work resume. Operation generations gate every continuation and late jobs are canceled server-side. | Resolved |
| R9/M10 | Older preference responses overwrite newer state | GET and PATCH operations applied independently. Loads coalesce, operations use a bounded FIFO queue, and stalled requests time out. | Resolved |

The deployment browser run also exposed a checklist-update/trash race: card mutations used the same
stale summary version concurrently. Per-note mutation queues now serialize checklist, move, trash,
and summary changes. The M8 E2E test now waits for the real post-close list refresh before searching.

## Finished-Diff Review Triage

| Severity | CodeRabbit finding | Disposition |
|---|---|---|
| Minor | Prove the auth guard promise remains unsettled before restoration | Valid test gap; assertion added. |
| Major | Invalidate pending history requests when Escape closes history | Valid; operation identity now gates history continuations. |
| Major | Duplicate `historyOpen` declaration in the editor spec | False positive; only one declaration existed. |
| Major | Cancel export/import jobs created after the dialog becomes inactive | Valid; both late-created job types are canceled. |
| Major | Cancel a live transfer job when closing from the error step | Valid; error joins working/conflict cleanup. |
| Minor | Call the admin settings save guard directly after load failure | Valid test gap; direct invocation added. |
| Major | Bound queued preference requests | Valid; GET/PATCH requests time out after 30 seconds so the queue advances. |

Detailed command outcomes are in [test-results.md](test-results.md).
