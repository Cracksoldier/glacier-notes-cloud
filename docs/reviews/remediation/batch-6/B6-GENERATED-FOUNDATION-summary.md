# Batch 6 Generated Client and Repository Foundation Remediation

## Scope

- Base commit: `1efb80579d7480de7652ca120cfe47050d84abe8`
- Original findings: the 11 unresolved R0/M0–M1 generator, metadata, startup-diagnostic,
  browser-title, and documentation findings
- Milestone gates: reproducible OpenAPI generation, generated-code drift detection, usable build
  commands, Angular production/test checks, `application/problem+json` diagnostics, and correlation
  IDs
- CodeRabbit: CLI 0.7.0, one `--uncommitted` review of the staged finished diff

Every original finding and the M0/M1 criteria were re-read and verified on the base. Regression
tests were added first. Before the fix, all three repository-contract tests and three of five focused
Angular tests failed for the expected reasons.

## Original Finding Resolution

| Original finding | Root cause and resolution | Final status |
|---|---|---|
| Generated `git_push.sh` can stage caller files and embeds a token URL | The default supporting-file set emitted an unsafe publisher. Generation now cleans its output and uses an explicit allowlist that excludes the script. | Resolved |
| Exploded arrays collapse in `query.params.ts#toString` | String interpolation flattened record arrays. A canonical template now delegates to Angular `HttpParams`, preserving repeated keys. | Resolved |
| Generated Angular package has incompatible tooling ranges | Generator defaults lagged Angular 22. Maven inputs and a package template now align Angular, RxJS, TypeScript, and ng-packagr. An independent package build is a CI gate. | Resolved |
| Startup failure drops safe detail and correlation ID | The root component discarded the HTTP error. It now maps the problem through `ProblemService`, renders an alert, and retains the reference. | Resolved |
| Contributor path, browser title, and frontend commands are stale | Scaffold metadata was never repository-customized. Paths, title, and supported npm/Playwright commands are corrected and contract-tested. | Resolved |
| TSV is spaces; repository URL is placeholder; `@angular/common` peer is absent | Upstream template defaults were incomplete. Canonical templates emit a tab and complete package metadata. | Resolved |

## Finished-Diff Review

The immutable [raw JSONL](B6-GENERATED-FOUNDATION.jsonl) has 16 records, a terminal
`review_completed` event, 21 reviewed files, 3 Minor findings, and SHA-256
`24c774b58e9bc6de669c630ce079f87315528d62ac367cdb6d85bb13fc869692`.

| Finding | Classification and final status |
|---|---|
| Overridden generated support files use four-space indentation | Valid. Both canonical support templates now emit the M0 two-space TypeScript format. |
| A single exploded value remains an array in `toRecord()` | Valid. The documented scalar shape is restored, with a focused regression test. |

CodeRabbit was not rerun. Post-review focused tests, all 63 frontend tests, the production build,
Biome checks, deterministic regeneration, and the independent generated-package build passed.
Complete command evidence is in [test-results.md](test-results.md).
