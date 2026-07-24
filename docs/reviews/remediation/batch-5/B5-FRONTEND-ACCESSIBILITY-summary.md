# Batch 5 Frontend Accessibility and Validation Remediation

## Scope

- Base commit: `7514d1b07f138849ccb8b68fef462a5ff1cb8f9a`
- Original findings: R1/M2, R2/M3, R3/M4, R5/M6, R6/M7, R8/M9, and R9/M10
- Milestone gates: visible and associated validation, deterministic async UI state, valid Markdown,
  stable upload identity, keyboard-contained dialogs, deterministic transfer tests, and complete
  English/German UI text for the affected surfaces
- CodeRabbit mode: one `--uncommitted` review of the finished implementation workspace

The findings and milestone acceptance criteria were re-read and every listed concern was reproduced
or verified on the Batch 5 base. Regression tests were added before production changes. The initial
focused run then failed 15 of 33 tests, directly exposing the targeted behavior. Detailed commands
and outcomes are in [test-results.md](test-results.md).

## Original Finding Resolution

| Review | Finding | Root cause and resolution | Final status |
|---|---|---|---|
| R1/M2 | Setup API/rate-limit feedback lacks tests | Rendering existed without behavioral coverage. Setup tests now exercise server and rate-limit feedback plus form/error associations. | Resolved |
| R1/M2 | Setup button uses invalid `font` shorthand | `inherit` was combined illegally with other shorthand values. The rule now uses valid inherited font, weight, and line-height declarations. | Resolved |
| R2/M3 | Admin status remains loading after failure | The observable had no error callback. Failures now replace loading with a problem alert. | Resolved |
| R2/M3 | Login fields omit required feedback | Invalid controls were only marked touched. Required messages now have stable IDs and are linked through `aria-describedby` and `aria-invalid`. | Resolved |
| R3/M4 | Reset and invitation tokens are cleartext | Bearer-token fields used ordinary text inputs. Both are masked and opt out of autocomplete. | Resolved |
| R3/M4 | Password reset permits concurrent submissions | The request had no single-flight guard. A busy state now prevents response-order races. | Resolved |
| R3/M4 | Deactivation leaves stale user actions | Successful non-self actions did not refresh the selected record. The current server state is now reloaded. | Resolved |
| R3/M4 | Initial admin-user errors are hidden | Alerts were nested beneath the loaded-user guard. Status and error output now remains visible without a user record. | Resolved |
| R3/M4 | Admin UI lacks component coverage | Admin settings coverage already existed; status and user-detail specs now cover loading failures, refresh behavior, visibility, and picker focus. | Resolved |
| R5/M6 | Multiline code produces a malformed fence | A symmetric inline wrapper placed the closing fence next to content. Block insertion now owns its newlines and preserves the original selection. | Resolved |
| R6/M7 | Image lightbox is not modal to keyboard users | The overlay asserted modality without focus management. Shared modal behavior now enters, traps, restores, handles Escape, and excludes background content. | Resolved |
| R6/M7 | Concurrent uploads use shifting indexes | Async callbacks captured array positions. Monotonic upload IDs now identify progress, errors, DOM rows, and completion removal. | Resolved |
| R8/M9 | Transfer E2E accumulates copied fixtures | Conflict handling selected `ADD_AS_COPIES`. The test now replaces the deterministic fixture IDs. | Resolved |
| R8/M9 | Transfer dialog lacks focus containment | The custom dialog had no focus lifecycle. It now uses the shared modal behavior and has keyboard regression coverage. | Resolved |
| R8/M9 | Transfer/admin file pickers hide focus | Styled labels did not expose focus on their hidden inputs. Both picker styles now provide a visible `:focus-within` outline. | Resolved |
| R9/M10 | Share warning and reasons are hard-coded | Heading, privacy notice, reasons, and actions bypassed `I18nService`. English and German keys now cover the complete warning. | Resolved |
| R9/M10 | Share warning lacks modal focus behavior | Focus could remain behind the warning. Shared modal handling now contains focus and restores every exit to the email-share trigger. | Resolved |
| R9/M10 | Notes settings overlay is partially English | Theme, sync, and account-settings text was literal. The affected overlay now renders through localized keys. | Resolved |

## Finished-Diff Review

CodeRabbit CLI 0.7.0 completed one uncommitted review. It reported one Minor finding across 27
tracked changed files. The immutable [raw JSONL](B5-FRONTEND-ACCESSIBILITY.jsonl) contains eight
records, ends with `review_completed`, and has SHA-256
`9898404c65fbbc086ce1ce459f8b9a3371bc63f685be658e78942cb44485572a`.

| Severity | Finding | Classification and final status |
|---|---|---|
| Minor | Reverse the overlapping-upload completion order in the regression test. | Not applicable. The original implementation captured array indexes. Completing the earlier row first shifts the later row and leaves it stuck, so the committed test order is the one that fails before the fix. Completing the later row first, as suggested, would pass the defective implementation and weaken the regression. |

CodeRabbit 0.7.0 did not list the three new untracked files in `reviewedFiles`; they were inspected
manually and exercised by the complete TypeScript build and test suite. Per the one-review rule, the
CLI was not run again.
