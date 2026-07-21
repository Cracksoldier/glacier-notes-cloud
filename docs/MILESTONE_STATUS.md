# Milestone Status

This file records implemented milestone scope. Acceptance criteria remain defined in
*GLACIER_NOTES_CLOUD_MILESTONES_BIOME.md*; a milestone is marked complete here only after its
implementation and repository verification gates pass.

| Milestone | Status | Delivered scope |
| --- | --- | --- |
| M0 | Complete | Monorepo, Quarkus/Angular foundations, OpenAPI generation, and CI conventions |
| M1 | Complete | PostgreSQL domain schema, owner-aware keys, migrations, and persistence boundaries |
| M2 | Complete | Secure container deployment, bootstrap flow, secrets, and operational health checks |
| M3 | Complete | Session authentication, CSRF protection, login throttling, and security headers |
| M4 | Complete | Invitations, user lifecycle administration, password resets, settings, and email delivery |
| M5 | Complete | Owner-scoped notebooks, notes, checklists, labels, archive, trash, conversion, and pagination APIs |
| M6–M13 | Pending | Not yet implemented |

## M5 Verification

M5 includes the canonical OpenAPI contract and generated Angular client, Flyway V5 constraints and
indexes, transactional content services, optimistic versions, stable keyset cursors, safe
cross-owner not-found behavior, and content-free tombstones retained for 30 days.

Verify the current milestone state with:

~~~bash
./mvnw verify
cd frontend
npm run check
npm run build:production
npm run test:ci
~~~
