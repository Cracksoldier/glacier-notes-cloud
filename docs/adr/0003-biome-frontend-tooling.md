# ADR 0003: Biome replaces Prettier

Status: Accepted

Biome 2.5.4 is the only frontend formatter and general-purpose linter. Angular compiler strictness
and `strictTemplates` cover framework semantics. `angular-eslint` is not installed because M0 has
no uncovered Angular-specific rule requirement; it may be added only for selected documented rules.

Angular HTML formatting remains enabled because the representative fixture compiles and its bindings
behave after `biome check --write`. Generated API sources are excluded and checked through drift.

