# ADR 0004: Owner-scoped portable identity

Status: Accepted

Portable content uses composite `(owner_id, id)` identity and composite foreign keys. The same UUID
may exist in different user accounts after importing a shared desktop export, but never grants read,
update, or relationship access across owners. Account, session, token, audit, and job IDs remain
globally unique.

