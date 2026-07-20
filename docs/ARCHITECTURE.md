# Architecture

Glacier Notes Cloud is an OpenAPI-first monorepo. The canonical contract is the source for
transport DTOs and API interfaces; persistence entities and domain types remain independent.

## Backend boundaries

- `generated`: build-generated REST contracts and DTOs
- `api`: HTTP adapters, safe problem mapping, and correlation IDs
- `application`: use cases and transaction boundaries
- `domain`: ownership-aware models and repository ports
- `persistence`: PostgreSQL mappings and owner-scoped repository adapters
- `security`, `storage`, `audit`, `jobs`, `configuration`: replaceable infrastructure boundaries

User-content authorization is enforced in application and persistence calls. Every owned
repository operation accepts `OwnerId`; an unscoped content lookup is not part of the public
repository interface. Administrators use the same ownership rules as normal users for their
own content.

Time and ID generation are injected interfaces so security, retention, and concurrency tests
can use deterministic implementations. Binary storage, outbound email, and password hashing are
also application ports; later milestones provide adapters without coupling use cases to vendors.

## Frontend boundaries

Angular uses standalone components, Router, generated OpenAPI services, and strict template
checking. Future note features should depend on platform-neutral data-access interfaces so the
portable model can remain compatible with the Electron desktop adapter.

## Coding conventions

- Keep generated DTOs out of domain and persistence signatures.
- Put transaction boundaries on application operations or repository writes, never UI code.
- Never log note content, checklist text, filenames, passwords, or tokens.
- Normalize identity values before persistence while preserving original display casing.
- Use server-authoritative UTC timestamps and optimistic versions for mutable content.
- Add an OpenAPI operation before implementing a new HTTP endpoint.
