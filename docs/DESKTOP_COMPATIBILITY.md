# Desktop compatibility

This is a thin pointer document for anyone integrating a desktop client against the same portable
format Glacier Notes Cloud reads and writes. It intentionally does not duplicate the user-facing
workflow, limits, and administration details in `docs/PORTABLE_TRANSFERS.md` — read that first for
the product-level contract.

## Schema-v1 field contract

The interchange format is `.glacier.json`, schema version 1, tracked against the baseline commit
recorded in `compatibility-fixtures/desktop-schema-v1/README.md`. The root envelope carries `format`,
`schemaVersion`, `exportedAt`, `notebooks`, `notes`, `labels`, `images`, and `scope`. Notebooks keep
their UUID, name, optional color, timestamps, and sort order. Notes keep their UUID, notebook ID,
`text`/`checklist` type, title, Markdown content, checklist items, image ID references, pin/archive/
color/label state, and timestamps. Images are embedded as base64 with a MIME type and original file
name. Cloud-only fields — owner ID and optimistic version — are deliberately never written into or
expected from a desktop export.

## The `glacier-img://<uuid>` scheme

Note content references embedded images by UUID, not by path or URL: `![alt](glacier-img://<uuid>)`.
Each referenced UUID must have a matching entry in the envelope's `images` array. A reader resolves
the scheme locally (from its own image store after import) rather than dereferencing it as a network
URL — Glacier Notes Cloud never fetches a `glacier-img://` reference remotely.

## Source of truth

- `compatibility-fixtures/desktop-schema-v1/` — real schema-v1 envelopes (`full`, `notebook`, `note`
  scope) generated from the desktop transfer model, covering default-notebook metadata, checklist
  IDs, labels, image data, and Markdown image references. Treat these fixtures, not this document, as
  the authoritative shape of the format.
- `backend/src/test/java/com/glaciernotes/cloud/application/transfer/PortableTransferCodecTest.java`
  — the backend's encode/decode round-trip tests against those fixtures.

After changing the portable contract on either side, re-verify every fixture against
`PortableTransferCodecTest` and confirm a fresh cloud export is still readable by the current desktop
application, per `docs/PORTABLE_TRANSFERS.md`.
