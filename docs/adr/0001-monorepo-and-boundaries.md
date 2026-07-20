# ADR 0001: Monorepo and architectural boundaries

Status: Accepted

Use one repository with independent Angular and Quarkus builds plus shared OpenAPI, documentation,
deployment, and compatibility areas. Generated transport types, domain logic, and persistence
entities stay in separate packages. This gives later desktop adapters a stable portable boundary.

