# Dependency and license inventory

Direct foundation dependencies are pinned in `backend/pom.xml`, `frontend/package.json`, and
`frontend/package-lock.json`. Transitive versions are resolved reproducibly by the Quarkus BOM,
Maven Wrapper, and npm lockfile.

| Component | Version | License |
|---|---:|---|
| Quarkus | 3.37.3 | Apache-2.0 |
| OpenAPI Generator | 7.24.0 | Apache-2.0 |
| PostgreSQL JDBC | Quarkus BOM | BSD-2-Clause |
| Flyway Community | Quarkus BOM | Apache-2.0 |
| Angular | 22.0.7 | MIT |
| RxJS | 7.8.x locked | Apache-2.0 |
| TypeScript | 6.0.x locked | Apache-2.0 |
| Vitest | 4.1.10 | MIT |
| Biome | 2.5.4 | MIT OR Apache-2.0 |

Review the complete resolved trees with `./mvnw -pl backend dependency:tree` and `npm ls --all`
from `frontend`. CI also runs npm audit. The remaining npm advisory, if still reported, is a
low-severity Windows-only Vite development-server issue in a transitive esbuild version; production
artifacts do not contain the development server.

