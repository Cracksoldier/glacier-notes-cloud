# Structured Review Evidence

This directory stores durable evidence from the M0–M10 CodeRabbit review program. Each review gets
its own milestone directory so the raw tool output, human disposition, and later test evidence remain
traceable to one immutable source range.

## Directory Convention

Use lowercase milestone scopes and stable review IDs:

```text
docs/reviews/
├── m0-m1/
│   ├── R0-M0-M1.jsonl
│   └── R0-M0-M1-summary.md
├── m2/
│   ├── R1-M2.jsonl
│   ├── R1-M2-summary.md
│   └── test-results.md
└── ...
```

Raw `.jsonl` files are immutable CodeRabbit CLI output. Corrections, severity changes, duplicates,
and false-positive decisions belong in the corresponding Markdown summary rather than in the raw
artifact.

## Required Evidence

For each review, record:

- source base and target commit;
- CodeRabbit CLI version and completion status;
- reviewed file and finding counts;
- raw-output SHA-256;
- human disposition for every finding;
- fixes or issues linked to confirmed findings;
- commands, environment profiles, and outcomes for later verification.

Test evidence belongs in `test-results.md` within the same milestone directory. Append distinct,
dated runs; do not replace an earlier failure with a later success.

## Safety and Repository Hygiene

- Never store API keys, cookies, CSRF values, passwords, bootstrap tokens, `.env` files, user notes,
  imported content, or unredacted logs here.
- Keep screenshots, traces, databases, exports, and other large or binary artifacts outside Git.
  Reference their CI artifact name or approved durable location from `test-results.md`.
- Redact tokens and personal data before committing command output.
- Do not commit a partial JSONL stream. Confirm a terminal `complete` event and no terminal `error`
  event first.

## Completed Reviews

| Review | Scope | Source | Result | Findings | Evidence |
|---|---|---|---|---:|---|
| R0 | M0–M1 | `9933582` | Completed | 15 | [Summary](m0-m1/R0-M0-M1-summary.md), [raw JSONL](m0-m1/R0-M0-M1.jsonl), [test results](m0-m1/test-results.md) |
| R1 | M2 | `9933582..b13b862` | Completed | 10 | [Summary](m2/R1-M2-summary.md), [raw JSONL](m2/R1-M2.jsonl), [test results](m2/test-results.md) |
| R2 | M3 | `b13b862..f83da35` | Completed | 9 | [Summary](m3/R2-M3-summary.md), [raw JSONL](m3/R2-M3.jsonl), [test results](m3/test-results.md) |
| R3 | M4 | `f83da35..08d3f76` | Completed | 13 | [Summary](m4/R3-M4-summary.md), [raw JSONL](m4/R3-M4.jsonl), [test results](m4/test-results.md) |
| R4 | M5 | `08d3f76..bf7e3f0` | Completed | 3 | [Summary](m5/R4-M5-summary.md), [raw JSONL](m5/R4-M5.jsonl), [test results](m5/test-results.md) |
| R5 | M6 | `bf7e3f0..e31f9e2` | Completed | 5 | [Summary](m6/R5-M6-summary.md), [raw JSONL](m6/R5-M6.jsonl), [test results](m6/test-results.md) |
| R6 | M7 | `e31f9e2..ec36449` | Completed | 15 | [Summary](m7/R6-M7-summary.md), [raw JSONL](m7/R6-M7.jsonl), [test results](m7/test-results.md) |
| R7 | M8 | `ec36449..2cf76f3` | Completed | 6 | [Summary](m8/R7-M8-summary.md), [raw JSONL](m8/R7-M8.jsonl), [test results](m8/test-results.md) |
| R8 | M9 | `2cf76f3..0f02251` | Completed | 14 | [Summary](m9/R8-M9-summary.md), [raw JSONL](m9/R8-M9.jsonl), [test results](m9/test-results.md) |
| R9 | M10 | `0f02251..ea3cce3` | Completed | 8 | [Summary](m10/R9-M10-summary.md), [raw JSONL](m10/R9-M10.jsonl), [test results](m10/test-results.md) |

## Remediation Reviews

| Batch | Scope | Base | Result | Evidence |
|---|---|---|---|---|
| Batch 3 | Frontend asynchronous state and lifecycle findings from R2, R3, R5, R7, R8, and R9 | `b5bbb0a` | 10 original concerns resolved; 6 valid new comments addressed; 1 false positive | [Summary](remediation/batch-3/B3-FRONTEND-ASYNC-summary.md), [raw JSONL](remediation/batch-3/B3-FRONTEND-ASYNC.jsonl), [test results](remediation/batch-3/test-results.md) |
| Batch 4 | Persistence ownership, schema integrity, pagination, and API/transfer contracts from R0, R2, R3, R4, R6, R7, and R8 | `c28f701` | 14 original concerns resolved; 4 valid new comments addressed | [Summary](remediation/batch-4/B4-PERSISTENCE-CONTRACTS-summary.md), [raw JSONL](remediation/batch-4/B4-PERSISTENCE-CONTRACTS.jsonl), [test results](remediation/batch-4/test-results.md) |
| Batch 5 | Frontend validation, interaction, accessibility, and localization findings from R1, R2, R3, R5, R6, R8, and R9 | `7514d1b` | 18 original concerns resolved; 1 new comment rejected after verification | [Summary](remediation/batch-5/B5-FRONTEND-ACCESSIBILITY-summary.md), [raw JSONL](remediation/batch-5/B5-FRONTEND-ACCESSIBILITY.jsonl), [test results](remediation/batch-5/test-results.md) |
