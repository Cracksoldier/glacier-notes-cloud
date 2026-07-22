# Portable Imports and Exports

Glacier Notes Cloud exchanges desktop-compatible schema-v1 `.glacier.json` files. The JSON root
contains `format`, `schemaVersion`, `exportedAt`, `notebooks`, `notes`, `labels`, `images`, and
`scope`. Images use base64 and note content references them as `glacier-img://<uuid>`. Cloud-only
metadata is optional and compatible readers should ignore it.

## User workflow

Open **Import / Export** in the notes toolbar. An export runs in the background and becomes
downloadable when its job succeeds. Imports first enter inspection, which validates structure,
relationships, UUIDs, image types, size limits, entity limits, and available quota without changing
permanent data. A conflict-free import preserves IDs. For conflicts, choose:

- **Add as copies** to deterministically remap entity IDs and all relationships while leaving
  existing content untouched.
- **Replace existing by ID** to update matching content owned by the target account. An ID owned by
  another account is neither accessed nor disclosed.

Closing or cancelling a pending job requests cancellation. Generated exports, inspected uploads,
and terminal job records expire after 24 hours by default. Successful imports delete their upload
immediately.

## Administration and limits

Administrators can disable user exports in **Administration → Settings**. A user's detail page also
accepts blind imports: only counts, conflicts, and structural errors are returned; titles, note
bodies, checklist text, image data, and filenames are never previewed. Successful blind applies are
audited.

Defaults permit a 1.5 GiB upload, 1 GiB of decoded images, 10 MiB per image, JSON nesting depth 32,
and strings up to 16 MiB. PNG, JPEG, and WebP are accepted; GIF is rejected. Operators can tune the
overall upload, decoded-image total, and retention with the variables documented in
[`deployment/README.md`](../deployment/README.md).

Compatibility samples live in `compatibility-fixtures/desktop-schema-v1`. After changing the
portable contract, verify all full, notebook, and note fixtures and ensure a cloud export remains
readable by the current desktop application.
