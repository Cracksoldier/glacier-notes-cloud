# ADR 0002: OpenAPI-first generation

Status: Accepted

`openapi/glacier-notes-v1.yaml` is canonical. Maven runs OpenAPI Generator 7.24.0 to create JAX-RS
interfaces/DTOs under build output and the committed Angular client. CI regenerates the client and
fails on drift. M0 defines shared conventions and a ping slice; later milestones add operations
before implementation instead of attempting the complete speculative v1 contract at once.

