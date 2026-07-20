# ADR 0005: Replaceable time, IDs, and PostgreSQL testing

Status: Accepted

Application code receives time and UUID generation through interfaces where deterministic tests need
them. Persistence integration tests use Quarkus Dev Services with PostgreSQL 18.3 rather than an
in-memory substitute. Flyway owns schema creation; Hibernate operates in validation-only mode.

