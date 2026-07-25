# ADR 0008: Release publication and signing process

- Status: Accepted
- Date: 2026-07-25

## Context

M13 requires a versioned, verifiably-published v1 artifact: an immutable OCI image, a software bill
of materials, and checksums/signatures "where supported." The project already builds and locally
scans a Docker image in CI (`ci.yml`'s `deployment` job) but publishes nothing — there was no registry
destination, no SBOM generation, and no signing.

## Decision

Publish to **GitHub Container Registry** (`ghcr.io/cracksoldier/glacier-notes-cloud`), authenticating
with the workflow's own `GITHUB_TOKEN` rather than a separate registry account/credential. This avoids
adding a new secret to manage and keeps publication tied to the same repository's permission model.

Generate the SBOM with **Syft** in CycloneDX JSON format against the built image (not the source
tree), so it reflects exactly what's inside the shipped artifact, including base-image packages.

Sign the image and attach the SBOM as a signed in-toto attestation with **cosign, using keyless
(OIDC) signing** rather than a managed key pair. GitHub Actions' built-in OIDC identity is sufficient
proof of provenance for this project's threat model (`docs/THREAT_MODEL.md`), and keyless signing
removes the operational burden — and compromise risk — of storing and rotating a private signing key
as a repository secret.

Releases are cut by pushing a `vX.Y.Z` git tag, which triggers `.github/workflows/release.yml`. That
workflow requires a prior successful `ci.yml` run for the same commit before it will build anything,
rather than re-running the full test suite itself — a tag push already re-triggers `ci.yml` in
parallel, so waiting on that second run would race the release workflow's own build. Once published,
the image tag is treated as immutable: the workflow refuses to push if the tag already exists in the
registry, and `latest` is the only floating tag (no floating major/minor tag is published while the
project is still on major version 0, per semver's "anything may change" convention).

## Consequences

A release requires no manual registry login or key management — only a tag push, which is why cutting
a real release is treated as the single deliberate, human-confirmed step in the release process (see
`docs/UPGRADE.md` and `GLACIER_NOTES_CLOUD_MILESTONES_BIOME.md` M13). Verification
(`cosign verify`) requires network access to Sigstore's transparency log; this project accepts that
as a supported operator burden, the same way it already expects operators to run `docker pull`.
Re-publishing a fixed version requires either a new patch tag or a deliberate, manual deletion of both
the git tag and the ghcr.io tag — never an automated overwrite.
