# ADR 0006: Durable external-storage reconciliation

- Status: Accepted
- Date: 2026-07-24

## Context

Image binaries and portable-transfer files live outside the Hibernate transaction that records
their metadata. A process failure between those mutations could leave an untracked binary, expose
metadata without both image variants, or roll back account deletion because physical cleanup was
temporarily unavailable.

## Decision

Glacier Notes records external-storage intent in `external_storage_operations`. New image writes
first commit a quota-counted rollback reservation. Metadata finalization consumes that reservation
in the same transaction; an interrupted write leaves it for cleanup. Logical deletion commits a
`DELETE_BINARY` or `DELETE_TRANSFER_FILE` operation in the transaction that removes access.

A scheduled worker claims operations with PostgreSQL row locks and expiring leases. Deletion is
idempotent and retryable with bounded exponential backoff. Transfer paths are normalized and must
remain below the configured temporary root. A backend mismatch or unsafe path is retained as a
terminal failure for operator investigation.

Transfer-linked creation reservations are not reclaimed while their job is queued or running.
Image storage keys used by imports are deterministic and all storage backends must accept a retry
of the same key. Pending binary operations keep the configured image backend immutable.

## Consequences

Logical deletion is immediate while physical cleanup is eventually consistent. Temporary storage
failures no longer invalidate database changes, but operators must monitor failed or repeatedly
retried operations. Quota reservations can conservatively reduce available space until recovery
completes.
